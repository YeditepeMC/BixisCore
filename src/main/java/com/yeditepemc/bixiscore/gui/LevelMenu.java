package com.yeditepemc.bixiscore.gui;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.manager.LeaderboardManager;
import com.yeditepemc.bixiscore.model.PlayerData;
import com.yeditepemc.bixiscore.reward.LevelReward;
import com.yeditepemc.bixiscore.reward.LevelRewards;
import com.yeditepemc.bixiscore.reward.RewardQueueManager;
import com.yeditepemc.bixiscore.util.TextUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Level menülerinin (ana ekran, leaderboard, milestone takvimi) görsellerini kurar.
 * Sadece "view" katmanı — tıklama mantığı {@link LevelMenuListener}'da.
 */
public class LevelMenu {

    // ---- Ana ekran slotları (36) ----
    static final int MAIN_SIZE = 36;
    static final int MAIN_PROGRESS = 11;
    static final int MAIN_SUMMARY = 13;
    static final int MAIN_MILESTONE = 15;
    static final int MAIN_LEADERBOARD = 29;
    static final int MAIN_CALENDAR = 31;
    static final int MAIN_INFO = 33;

    // ---- Takvim slotları (54) ----
    static final int CAL_SIZE = 54;
    static final int CAL_SUMMARY = 50;
    static final int CAL_BACK = 53;

    // ---- Leaderboard slotları (54) ----
    static final int LB_SIZE = 54;
    static final int LB_LIMIT = 45;   // slot 0..44
    static final int LB_BACK = 49;
    static final int LB_CLOSE = 53;

    private static final int BAR_LEN = 10;
    private static final int[] MILESTONES = {10, 20, 30, 40, 50};

    private final BixisCorePlugin plugin;
    private final LevelRewards rewards;
    private final RewardQueueManager queue;
    private final LeaderboardManager leaderboard;

    public LevelMenu(BixisCorePlugin plugin, LevelRewards rewards,
                     RewardQueueManager queue, LeaderboardManager leaderboard) {
        this.plugin = plugin;
        this.rewards = rewards;
        this.queue = queue;
        this.leaderboard = leaderboard;
    }

    // ==================================================================
    //  Ana ekran (2.1)
    // ==================================================================

    public void openMain(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayer(player.getUniqueId());
        if (data == null) {
            player.sendMessage(TextUtil.comp("&cVerin henüz yüklenmedi, birazdan tekrar dene."));
            return;
        }
        Inventory inv = create(LevelMenuHolder.Type.MAIN, MAIN_SIZE, "&1&lLevel İlerlemen");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        int level = data.getLevel();
        boolean capped = level >= PlayerData.MAX_LEVEL;

        // İlerleme çubuğu
        List<String> progLore = new ArrayList<>();
        if (capped) {
            progLore.add("&6&lMAKS SEVİYE");
            progLore.add("&7" + bar(1, 1));
            progLore.add("&7Toplam XP: &f" + TextUtil.num(data.getXp()));
            progLore.add("&8Cap sonrası XP birikmeye devam ediyor.");
        } else {
            long into = data.getXpIntoLevel();
            long span = PlayerData.xpForNextLevel(level);
            progLore.add("&7Level &f" + level + " &7→ &f" + (level + 1));
            progLore.add("&7" + bar(into, span));
            progLore.add("&f" + TextUtil.num(into) + " &7/ &f" + TextUtil.num(span) + " &7XP");
            progLore.add("&7Kalan: &e" + TextUtil.num(data.getXpToNextLevel()) + " XP");
        }
        inv.setItem(MAIN_PROGRESS, TextUtil.item(Material.EXPERIENCE_BOTTLE, "&aİlerleme", progLore));

        // Özet (player head)
        inv.setItem(MAIN_SUMMARY, summaryHead(player, data));

        // Sonraki milestone önizlemesi
        inv.setItem(MAIN_MILESTONE, milestonePreview(level));

        // Butonlar
        inv.setItem(MAIN_LEADERBOARD, TextUtil.item(Material.DIAMOND, "&b&lLeaderboard",
                List.of("&7En yüksek toplam XP'li oyuncular.", "&eTıkla ve gör »")));
        inv.setItem(MAIN_CALENDAR, TextUtil.item(Material.CHEST_MINECART, "&e&lMilestone Takvimi",
                List.of("&7Level ödüllerini gör ve topla.", "&eTıkla ve aç »")));
        inv.setItem(MAIN_INFO, infoItem());

        player.openInventory(inv);
    }

    private ItemStack milestonePreview(int level) {
        int next = -1;
        for (int m : MILESTONES) {
            if (m > level) {
                next = m;
                break;
            }
        }
        if (next == -1) {
            return TextUtil.item(Material.NETHER_STAR, "&6&lTüm Milestone'lar Tamamlandı",
                    List.of("&7Level 50'ye ulaştın — tebrikler!"));
        }
        LevelReward r = rewards.getReward(next);
        List<String> lore = new ArrayList<>();
        lore.add("&7Sıradaki büyük ödül: &fLevel " + next);
        lore.addAll(rewardLines(r, false));
        return TextUtil.item(Material.ENDER_CHEST, "&6&lSıradaki Milestone", lore);
    }

    private ItemStack infoItem() {
        List<String> lore = plugin.getConfig().getStringList("xp-info");
        if (lore.isEmpty()) {
            lore = List.of("&7XP kazanmak için achievement'ları,",
                    "&7günlük ve haftalık ödülleri tamamla.");
        }
        return TextUtil.item(Material.WRITABLE_BOOK, "&f&lXP Nasıl Kazanılır?", lore);
    }

    // ==================================================================
    //  Milestone takvimi (2.3) — 54 slotluk advent görünümü
    // ==================================================================

    public void openCalendar(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayer(player.getUniqueId());
        if (data == null) {
            player.sendMessage(TextUtil.comp("&cVerin henüz yüklenmedi, birazdan tekrar dene."));
            return;
        }
        Inventory inv = create(LevelMenuHolder.Type.CALENDAR, CAL_SIZE, "&e&lMilestone Takvimi");

        int playerLevel = data.getLevel();
        Set<Integer> claimed = queue.getClaimed(player.getUniqueId());

        for (int level = 1; level <= PlayerData.MAX_LEVEL; level++) {
            inv.setItem(level - 1, calendarSlot(level, playerLevel, claimed.contains(level)));
        }

        inv.setItem(CAL_SUMMARY, summaryHead(player, data));
        inv.setItem(51, TextUtil.item(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(52, TextUtil.item(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(CAL_BACK, TextUtil.item(Material.ARROW, "&e« Ana ekrana dön"));

        player.openInventory(inv);
    }

    /** Bir takvim slotunun materyali + lore'unu duruma göre kurar (CLAUDE.md 2.3 tablosu). */
    private ItemStack calendarSlot(int level, int playerLevel, boolean claimed) {
        LevelReward reward = rewards.getReward(level);
        boolean milestone = reward.milestone();
        boolean reached = RewardQueueManager.isReached(level, playerLevel);
        boolean current = level == playerLevel && playerLevel < PlayerData.MAX_LEVEL;

        List<String> lore = new ArrayList<>();
        if (milestone) {
            lore.add("&6&l★ ÖZEL ÖDÜL ★");
            lore.add("");
        }

        Material material;
        String name;
        if (!reached && !current) {
            // Henüz ulaşılmamış
            material = Material.GRAY_STAINED_GLASS_PANE;
            name = "&7Level " + level + " &8(kilitli)";
            lore.addAll(rewardLines(reward, true));
            lore.add("");
            lore.add("&cBu leveli henüz açmadın!");
        } else if (current) {
            // Şu anki level — bilgi amaçlı
            material = Material.LIME_STAINED_GLASS_PANE;
            name = "&a&lLevel " + level + " &7(şu an buradasın)";
            lore.addAll(rewardLines(reward, false));
            lore.add("");
            lore.add("&7Bu levelin ödülü bir sonraki");
            lore.add("&7levele geçince açılır.");
        } else if (!claimed) {
            // Ulaşılmış + ödül alınmamış
            material = Material.CHEST_MINECART;
            name = "&e&lLevel " + level + " &a- Ödül hazır!";
            lore.addAll(rewardLines(reward, false));
            lore.add("");
            lore.add("&aTıkla ve ödülü al!");
        } else {
            // Ulaşılmış + ödül alınmış
            material = Material.MINECART;
            name = "&7Level " + level + " &8- alındı";
            lore.addAll(rewardLines(reward, true));
            lore.add("");
            lore.add("&8Bu ödülü zaten aldın.");
        }
        return TextUtil.item(material, name, lore);
    }

    // ==================================================================
    //  Leaderboard (2.2) — asenkron (DB sorgusu)
    // ==================================================================

    public void openLeaderboard(Player player) {
        leaderboard.topByXp(LB_LIMIT).thenAccept(entries ->
                Bukkit.getScheduler().runTask(plugin, () -> buildAndOpenLeaderboard(player, entries))
        ).exceptionally(ex -> {
            plugin.getLogger().warning("Leaderboard yüklenemedi: " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(TextUtil.comp("&cLeaderboard şu an yüklenemedi.")));
            return null;
        });
    }

    private void buildAndOpenLeaderboard(Player player, List<LeaderboardManager.Entry> entries) {
        Inventory inv = create(LevelMenuHolder.Type.LEADERBOARD, LB_SIZE, "&b&lLeaderboard &7(Toplam XP)");

        int slot = 0;
        int rank = 1;
        for (LeaderboardManager.Entry e : entries) {
            if (slot >= LB_LIMIT) {
                break;
            }
            List<String> lore = List.of(
                    "&7Level: &f" + e.level(),
                    "&7Toplam XP: &f" + TextUtil.num(e.xp())
            );
            inv.setItem(slot, playerHead(e.uuid(), "&e#" + rank + " &f" + e.username(), lore));
            slot++;
            rank++;
        }
        if (entries.isEmpty()) {
            inv.setItem(22, TextUtil.item(Material.BARRIER, "&7Henüz veri yok",
                    List.of("&8Kayıtlı oyuncu bulunamadı.")));
        }

        inv.setItem(LB_BACK, TextUtil.item(Material.ARROW, "&e« Ana ekrana dön"));
        inv.setItem(LB_CLOSE, TextUtil.item(Material.BARRIER, "&cKapat"));
        player.openInventory(inv);
    }

    // ==================================================================
    //  Ortak yardımcılar
    // ==================================================================

    private ItemStack summaryHead(Player player, PlayerData data) {
        Economy economy = BixisCorePlugin.getEconomy();
        String coin = economy != null ? TextUtil.num((long) economy.getBalance(player)) : "&8(Vault yok)";
        List<String> lore = List.of(
                "&7Seviye: &e" + data.getLevel() + "&7/&e" + PlayerData.MAX_LEVEL,
                "&7Toplam XP: &f" + TextUtil.num(data.getXp()),
                "&7Coin: &6" + coin,
                "&7Streak: &f" + data.getStreakDays() + " gün"
        );
        return playerHead(player.getUniqueId(), "&e&l" + player.getName(), lore);
    }

    private ItemStack playerHead(java.util.UUID uuid, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(owner);
            meta.displayName(TextUtil.comp(name));
            meta.lore(TextUtil.comps(lore));
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Ödül satırları. {@code muted} true ise renkler soluk (kilitli/alınmış görünüm). */
    private List<String> rewardLines(LevelReward r, boolean muted) {
        String bullet = muted ? "&8• " : "&e• ";
        List<String> lore = new ArrayList<>();
        lore.add(muted ? "&8Ödül:" : "&7Ödül:");
        lore.add(bullet + TextUtil.num(r.coins()) + " Coin");
        if (r.hasCrate()) {
            lore.add(bullet + r.crateTier() + "★ Kasa");
        }
        if (r.hasTitle()) {
            lore.add((muted ? "&8• " : "&6• ") + "Özel Unvan/Rozet");
        }
        return lore;
    }

    private String bar(long into, long span) {
        double ratio = span <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, (double) into / span));
        int filled = (int) Math.round(ratio * BAR_LEN);
        int percent = (int) Math.round(ratio * 100);
        return "&a" + "■".repeat(filled) + "&7" + "■".repeat(BAR_LEN - filled) + " &f" + percent + "%";
    }

    private Inventory create(LevelMenuHolder.Type type, int size, String title) {
        LevelMenuHolder holder = new LevelMenuHolder(type);
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.comp(title));
        holder.setInventory(inv);
        return inv;
    }

    private void fill(Inventory inv, Material filler) {
        ItemStack pane = TextUtil.item(filler, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }
}
