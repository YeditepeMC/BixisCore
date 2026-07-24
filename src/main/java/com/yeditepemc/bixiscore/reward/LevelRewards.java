package com.yeditepemc.bixiscore.reward;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.model.PlayerData;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Level başına ödül formülü + milestone istisnaları (CLAUDE.md 2.4).
 *
 * <ul>
 *   <li>Normal level: coin = level × {@code coin-per-level} (varsayılan 50)</li>
 *   <li>Milestone (10/20/30/40/50): coin = level × {@code milestone-coin-per-level} (varsayılan 100)</li>
 *   <li>Kasa kademesi belirli levellerde: 5,15→1★ · 25,35,45→2★ · 10,20→3★ · 30,40→4★ · 50→5★</li>
 *   <li>Level 50: ek olarak özel unvan (LuckPerms komutu ile)</li>
 * </ul>
 *
 * Kozmetik/permission ödül tipi ileride eklenebilir (esnek bırakıldı — bkz. 2.4).
 */
public class LevelRewards {

    private final long coinPerLevel;
    private final long milestoneCoinPerLevel;

    /** tier (1-5) → konsol komutu şablonu ({player} yer tutucusu). Boşsa teslim atlanır. */
    private final Map<Integer, String> crateCommands = new HashMap<>();
    /** Level 50 unvan komutu ({player} yer tutucusu). */
    private final String titleCommand;

    public LevelRewards(BixisCorePlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();
        this.coinPerLevel = cfg.getLong("level-rewards.coin-per-level", 50L);
        this.milestoneCoinPerLevel = cfg.getLong("level-rewards.milestone-coin-per-level", 100L);
        for (int tier = 1; tier <= 5; tier++) {
            crateCommands.put(tier, cfg.getString("level-rewards.crate-commands." + tier, ""));
        }
        this.titleCommand = cfg.getString("level-rewards.level50-title-command", "");
    }

    /** Verilen level'ın ödülünü formülle hesaplar. */
    public LevelReward getReward(int level) {
        boolean milestone = isMilestone(level);
        int crateTier = crateTierFor(level);
        long coins = (milestone ? milestoneCoinPerLevel : coinPerLevel) * level;
        boolean hasTitle = (level == PlayerData.MAX_LEVEL);
        return new LevelReward(level, coins, crateTier, milestone, hasTitle);
    }

    public static boolean isMilestone(int level) {
        return level % 10 == 0 && level >= 10 && level <= PlayerData.MAX_LEVEL;
    }

    /** Kasa kademesi; kasa yoksa 0. */
    private int crateTierFor(int level) {
        return switch (level) {
            case 5, 15 -> 1;
            case 25, 35, 45 -> 2;
            case 10, 20 -> 3;
            case 30, 40 -> 4;
            case 50 -> 5;
            default -> 0;
        };
    }

    /** tier için yapılandırılmış konsol komutu ({player} çözülmemiş); yoksa boş. */
    public String crateCommand(int tier) {
        return crateCommands.getOrDefault(tier, "");
    }

    public String titleCommand() {
        return titleCommand;
    }
}
