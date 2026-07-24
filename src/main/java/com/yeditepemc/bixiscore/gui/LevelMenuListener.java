package com.yeditepemc.bixiscore.gui;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.model.PlayerData;
import com.yeditepemc.bixiscore.reward.LevelReward;
import com.yeditepemc.bixiscore.reward.LevelRewards;
import com.yeditepemc.bixiscore.reward.RewardQueueManager;
import com.yeditepemc.bixiscore.util.TextUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Level menülerinin tıklama mantığı: sekme geçişleri, ödül talep/teslim,
 * ses efektleri (CLAUDE.md 2.3).
 */
public class LevelMenuListener implements Listener {

    private final BixisCorePlugin plugin;
    private final LevelMenu menu;
    private final RewardQueueManager queue;
    private final LevelRewards rewards;

    public LevelMenuListener(BixisCorePlugin plugin, LevelMenu menu,
                             RewardQueueManager queue, LevelRewards rewards) {
        this.plugin = plugin;
        this.menu = menu;
        this.queue = queue;
        this.rewards = rewards;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LevelMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LevelMenuHolder holder)) {
            return;
        }
        event.setCancelled(true); // menüde item taşımayı tamamen engelle

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Alt (oyuncu) envanterine yapılan tıklamaları yok say
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        int slot = event.getRawSlot();

        switch (holder.getType()) {
            case MAIN -> handleMain(player, slot);
            case LEADERBOARD -> handleLeaderboard(player, slot);
            case CALENDAR -> handleCalendar(player, slot);
        }
    }

    private void handleMain(Player player, int slot) {
        switch (slot) {
            case LevelMenu.MAIN_LEADERBOARD -> {
                click(player);
                menu.openLeaderboard(player);
            }
            case LevelMenu.MAIN_CALENDAR -> {
                click(player);
                menu.openCalendar(player);
            }
            default -> {
                // bilgi item'ları — işlem yok
            }
        }
    }

    private void handleLeaderboard(Player player, int slot) {
        if (slot == LevelMenu.LB_BACK) {
            click(player);
            menu.openMain(player);
        } else if (slot == LevelMenu.LB_CLOSE) {
            player.closeInventory();
        }
    }

    private void handleCalendar(Player player, int slot) {
        if (slot == LevelMenu.CAL_BACK) {
            click(player);
            menu.openMain(player);
            return;
        }
        if (slot < 0 || slot > 49) {
            return; // özet/dolgu slotları
        }

        int level = slot + 1;
        PlayerData data = plugin.getPlayerDataManager().getPlayer(player.getUniqueId());
        if (data == null) {
            player.sendMessage(TextUtil.comp("&cVerin henüz yüklenmedi."));
            return;
        }
        int playerLevel = data.getLevel();
        boolean reached = RewardQueueManager.isReached(level, playerLevel);
        boolean current = level == playerLevel && playerLevel < PlayerData.MAX_LEVEL;
        boolean claimed = queue.isClaimed(player.getUniqueId(), level);

        if (!reached && !current) {
            bad(player);
            player.sendMessage(TextUtil.comp("&cLevel " + level + "'i henüz açmadın!"));
        } else if (current) {
            click(player);
            long into = data.getXpIntoLevel();
            long span = PlayerData.xpForNextLevel(level);
            player.sendMessage(TextUtil.comp("&7Level &f" + level + " &7→ &f" + (level + 1)
                    + " &8| &f" + TextUtil.num(into) + "&7/&f" + TextUtil.num(span) + " XP"));
            player.sendMessage(TextUtil.comp("&7Bu levelin ödülü, bir sonraki levele geçince açılır."));
        } else if (!claimed) {
            RewardQueueManager.ClaimResult result = queue.claim(player, level, playerLevel);
            switch (result) {
                case SUCCESS -> {
                    good(player);
                    player.sendMessage(TextUtil.comp("&aLevel " + level + " ödülünü aldın: "
                            + rewardSummary(level)));
                    menu.openCalendar(player); // görseli yenile (MINECART'a döner)
                }
                case ALREADY_CLAIMED -> {
                    bad(player);
                    player.sendMessage(TextUtil.comp("&cBu ödülü zaten aldın!"));
                    menu.openCalendar(player);
                }
                case NO_ECONOMY -> player.sendMessage(TextUtil.comp(
                        "&cEkonomi sistemi şu an kullanılamıyor, ödül teslim edilemedi."));
                case NOT_REACHED -> {
                    bad(player);
                    player.sendMessage(TextUtil.comp("&cBu leveli henüz açmadın!"));
                }
            }
        } else {
            bad(player);
            player.sendMessage(TextUtil.comp("&cBu ödülü zaten aldın!"));
        }
    }

    private String rewardSummary(int level) {
        LevelReward r = rewards.getReward(level);
        StringBuilder sb = new StringBuilder("&e" + TextUtil.num(r.coins()) + " Coin");
        if (r.hasCrate()) {
            sb.append("&7, &e").append(r.crateTier()).append("★ Kasa");
        }
        if (r.hasTitle()) {
            sb.append(" &6+ Özel Unvan");
        }
        return sb.toString();
    }

    private void good(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private void bad(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
    }
}
