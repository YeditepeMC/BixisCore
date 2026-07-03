package com.yeditepemc.bixiscore.api;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.event.LevelUpEvent;
import com.yeditepemc.bixiscore.manager.PlayerDataManager;
import com.yeditepemc.bixiscore.model.PlayerData;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Diğer pluginlerin (BixisRewards, BixisAchievements, BixisNavigator vb.)
 * kullandığı public API. {@code BixisCorePlugin.getAPI()} ile erişilir.
 *
 * <p>Tüm mesajlar Türkçedir (CLAUDE.md — Dil).
 */
public class BixisCoreAPI {

    private final BixisCorePlugin plugin;
    private final PlayerDataManager dataManager;

    public BixisCoreAPI(BixisCorePlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    // ------------------------------------------------------------------
    //  XP / Level
    // ------------------------------------------------------------------

    /**
     * Oyuncuya XP ekler, seviyeyi yeniden hesaplar ve seviye atlandıysa
     * {@link LevelUpEvent} fırlatır.
     *
     * @return XP eklenebildiyse {@code true} (oyuncu verisi cache'de yüklüyse)
     */
    public boolean addXP(Player player, long amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerData data = getPlayerData(player);
        if (data == null) {
            return false;
        }

        int oldLevel = data.getLevel();
        data.addXp(amount);
        int newLevel = data.getLevel();
        dataManager.savePlayer(data);

        player.sendMessage("§a+§e" + amount + " §aXP kazandın!");

        if (newLevel > oldLevel) {
            // Seviye atlama bildirimi (mesaj + ses) LevelUpEvent dinleyicisine aittir.
            // Event her zaman ana thread'de çağrılır.
            LevelUpEvent event = new LevelUpEvent(player, oldLevel, newLevel);
            if (Bukkit.isPrimaryThread()) {
                Bukkit.getPluginManager().callEvent(event);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(event));
            }
        }
        return true;
    }

    /**
     * Oyuncunun mevcut seviyesi. Veri yüklü değilse {@code 1} döner.
     */
    public int getLevel(Player player) {
        PlayerData data = getPlayerData(player);
        return data == null ? 1 : data.getLevel();
    }

    /**
     * Oyuncunun toplam XP'si. Veri yüklü değilse {@code 0} döner.
     */
    public long getXP(Player player) {
        PlayerData data = getPlayerData(player);
        return data == null ? 0L : data.getXp();
    }

    // ------------------------------------------------------------------
    //  Coin (ekonomi) — Vault üzerinden, evrensel (CLAUDE.md — Ekonomi)
    // ------------------------------------------------------------------

    /**
     * Oyuncunun hesabına coin ekler (Vault deposit).
     *
     * @return işlem başarılıysa {@code true}; ekonomi yoksa/hata olursa {@code false}
     */
    public boolean addCoins(Player player, long amount) {
        if (amount <= 0) {
            return false;
        }
        Economy economy = BixisCorePlugin.getEconomy();
        if (economy == null) {
            player.sendMessage("§cEkonomi sistemi şu anda kullanılamıyor.");
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage("§cCoin eklenirken bir hata oluştu.");
            return false;
        }
        player.sendMessage("§a+§e" + amount + " coin §ahesabına eklendi!");
        return true;
    }

    /**
     * Oyuncudan coin siler (Vault withdraw). Yeterli coin yoksa işlem yapılmaz.
     *
     * @return işlem başarılıysa {@code true}; bakiye yetersizse/hata olursa {@code false}
     */
    public boolean removeCoins(Player player, long amount) {
        if (amount <= 0) {
            return false;
        }
        Economy economy = BixisCorePlugin.getEconomy();
        if (economy == null) {
            player.sendMessage("§cEkonomi sistemi şu anda kullanılamıyor.");
            return false;
        }
        if (!economy.has(player, amount)) {
            player.sendMessage("§cYeterli coinin yok! §7(Gerekli: §e" + amount
                    + "§7, Mevcut: §e" + (long) economy.getBalance(player) + "§7)");
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage("§cCoin düşülürken bir hata oluştu.");
            return false;
        }
        player.sendMessage("§c-§e" + amount + " coin §chesabından düşüldü.");
        return true;
    }

    /**
     * Oyuncunun mevcut coin bakiyesi (Vault). Ekonomi yoksa {@code 0} döner.
     */
    public long getCoins(Player player) {
        Economy economy = BixisCorePlugin.getEconomy();
        return economy == null ? 0L : (long) economy.getBalance(player);
    }

    // ------------------------------------------------------------------
    //  Ham veri
    // ------------------------------------------------------------------

    /**
     * Oyuncunun cache'deki verisini döner. Oyuncu online değilse {@code null}.
     */
    public PlayerData getPlayerData(Player player) {
        return dataManager.getPlayer(player.getUniqueId());
    }
}
