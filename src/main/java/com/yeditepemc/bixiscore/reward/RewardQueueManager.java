package com.yeditepemc.bixiscore.reward;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Level ödül kuyruğunu ({@code bixiscore_level_reward_queue}) yöneten singleton.
 * Bu kuyruk BixisCore'a özeldir — başka plugin (BixisAchievements) ile paylaşılmaz
 * (CLAUDE.md 2.5; mimari bilerek bağımsız).
 *
 * <p>Talep edilen (claim) levellerin seti online oyuncular için bellekte tutulur.
 * Ödül teslimi: coin Vault üzerinden, kasa/unvan yapılandırılabilir konsol
 * komutlarıyla (esnek — ağda henüz kasa eklentisi yok).
 */
public class RewardQueueManager implements Listener {

    /** Kuyruk durumları — PENDING ileride sunucular-arası gecikmeli teslim için ayrılmıştır. */
    public static final String STATUS_DELIVERED = "DELIVERED";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final BixisCorePlugin plugin;
    private final DatabaseManager database;
    private final LevelRewards rewards;

    /** uuid → talep edilmiş level seti (online oyuncular). */
    private final ConcurrentHashMap<UUID, Set<Integer>> claimedCache = new ConcurrentHashMap<>();

    public RewardQueueManager(BixisCorePlugin plugin, DatabaseManager database, LevelRewards rewards) {
        this.plugin = plugin;
        this.database = database;
        this.rewards = rewards;
    }

    // ------------------------------------------------------------------
    //  Cache yükleme
    // ------------------------------------------------------------------

    public void loadClaimed(UUID uuid) {
        database.executeQuery(
                "SELECT level FROM bixiscore_level_reward_queue WHERE uuid = ?",
                rs -> {
                    Set<Integer> set = ConcurrentHashMap.newKeySet();
                    while (rs.next()) {
                        set.add(rs.getInt("level"));
                    }
                    return set;
                },
                uuid.toString()
        ).thenAccept(set -> claimedCache.put(uuid, set)).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Ödül kuyruğu yüklenemedi: " + uuid, ex);
            claimedCache.putIfAbsent(uuid, ConcurrentHashMap.newKeySet());
            return null;
        });
    }

    public boolean isClaimed(UUID uuid, int level) {
        Set<Integer> set = claimedCache.get(uuid);
        return set != null && set.contains(level);
    }

    /** Salt-okunur kopya (GUI için). */
    public Set<Integer> getClaimed(UUID uuid) {
        Set<Integer> set = claimedCache.get(uuid);
        return set == null ? Collections.emptySet() : new HashSet<>(set);
    }

    // ------------------------------------------------------------------
    //  Talep + teslim
    // ------------------------------------------------------------------

    /** Talebin neden reddedildiğini/başarıldığını temsil eder. */
    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        NOT_REACHED,
        NO_ECONOMY
    }

    /**
     * Bir level ödülünü talep eder ve teslim eder. Ana thread'de çağrılmalıdır
     * (Vault işlemleri ve komut dispatch ana thread ister).
     *
     * @param playerLevel oyuncunun mevcut level'ı (yetki kontrolü için)
     */
    public ClaimResult claim(Player player, int level, int playerLevel) {
        UUID uuid = player.getUniqueId();

        if (!isReached(level, playerLevel)) {
            return ClaimResult.NOT_REACHED;
        }
        if (isClaimed(uuid, level)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        LevelReward reward = rewards.getReward(level);

        // Coin teslimi — ekonomi yoksa talebi tamamlama (coin kaybını önle)
        Economy economy = BixisCorePlugin.getEconomy();
        if (reward.coins() > 0) {
            if (economy == null) {
                return ClaimResult.NO_ECONOMY;
            }
            economy.depositPlayer(player, reward.coins());
        }

        // Kasa teslimi — yapılandırılmış konsol komutu (varsa)
        if (reward.hasCrate()) {
            String cmd = rewards.crateCommand(reward.crateTier());
            dispatch(cmd, player);
        }

        // Level 50 unvan
        if (reward.hasTitle()) {
            dispatch(rewards.titleCommand(), player);
        }

        // Cache + kalıcı kayıt (talep edilmiş olarak işaretle)
        claimedCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(level);
        persist(uuid, level);

        return ClaimResult.SUCCESS;
    }

    /**
     * Bir level "ulaşılmış" (ödül talep edilebilir) mi?
     * Kural: level &lt; mevcut level. Cap istisnası: oyuncu 50'deyse level 50 de ulaşılmıştır
     * (çünkü 51'e geçilemez — bkz. CLAUDE.md 2.3/2.6).
     */
    public static boolean isReached(int level, int playerLevel) {
        if (level < playerLevel) {
            return true;
        }
        return level == playerLevel && playerLevel >= com.yeditepemc.bixiscore.model.PlayerData.MAX_LEVEL;
    }

    private void dispatch(String template, Player player) {
        if (template == null || template.isBlank()) {
            plugin.getLogger().info("Ödül komutu yapılandırılmamış (atlandı) — oyuncu: " + player.getName());
            return;
        }
        String cmd = template.replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void persist(UUID uuid, int level) {
        String now = LocalDateTime.now().format(FMT);
        database.executeUpdate(upsertSql(), uuid.toString(), level, STATUS_DELIVERED, now, now)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Ödül kuyruğu kaydedilemedi: " + uuid + " level " + level, ex);
                    return null;
                });
    }

    private String upsertSql() {
        if (database.isMySQL()) {
            return "INSERT INTO bixiscore_level_reward_queue " +
                   "(uuid, level, status, created_at, delivered_at) VALUES (?, ?, ?, ?, ?) " +
                   "ON DUPLICATE KEY UPDATE status = VALUES(status), delivered_at = VALUES(delivered_at)";
        }
        return "INSERT INTO bixiscore_level_reward_queue " +
               "(uuid, level, status, created_at, delivered_at) VALUES (?, ?, ?, ?, ?) " +
               "ON CONFLICT(uuid, level) DO UPDATE SET status = excluded.status, delivered_at = excluded.delivered_at";
    }

    // ------------------------------------------------------------------
    //  Event dinleyicileri
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        loadClaimed(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        claimedCache.remove(event.getPlayer().getUniqueId());
    }
}
