package com.yeditepemc.bixiscore.placeholder;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.model.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * BixisCore PlaceholderAPI genişletmesi.
 * Diğer eklentiler (tab, scoreboard, hologram vb.) {@code %bixiscore_...%}
 * placeholder'larıyla oyuncu seviye/XP verisine erişebilir.
 *
 * <p>Veri henüz yüklenmemişse tüm placeholder'lar {@code "N/A"} döner.
 */
public class BixisCorePlaceholders extends PlaceholderExpansion {

    /** İlerleme çubuğundaki toplam karakter sayısı. */
    private static final int BAR_LENGTH = 10;
    private static final String NOT_AVAILABLE = "N/A";

    private final BixisCorePlugin plugin;

    public BixisCorePlaceholders(BixisCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bixiscore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "iBerkS";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** /papi reload sonrası kayıtlı kalması için. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return NOT_AVAILABLE;
        }
        PlayerData data = plugin.getPlayerDataManager().getPlayer(player.getUniqueId());
        if (data == null) {
            return NOT_AVAILABLE;
        }

        return switch (params.toLowerCase()) {
            case "level" -> String.valueOf(data.getLevel());
            case "xp" -> String.valueOf(data.getXp());
            case "xp_needed" -> String.valueOf(data.getXpToNextLevel());
            case "xp_progress" -> String.valueOf(data.getXpIntoLevel());
            case "progress_bar" -> buildProgressBar(data);
            case "level_formatted" -> "§e⭐ Level " + data.getLevel();
            // Bilinmeyen placeholder — PAPI'ye "işlenmedi" bildir
            default -> null;
        };
    }

    /**
     * "§a■■■■■§7■■■■■ §f50%" biçiminde 10 karakterlik görsel ilerleme çubuğu.
     * Yeşil = dolu, gri = boş.
     */
    private String buildProgressBar(PlayerData data) {
        double ratio;
        if (data.getLevel() >= PlayerData.MAX_LEVEL) {
            ratio = 1.0; // cap'te bar tam dolu
        } else {
            long into = data.getXpIntoLevel();
            long span = PlayerData.xpForNextLevel(data.getLevel());
            ratio = span <= 0 ? 1.0 : (double) into / span;
        }
        ratio = Math.max(0.0, Math.min(1.0, ratio));

        int filled = (int) Math.round(ratio * BAR_LENGTH);
        int empty = BAR_LENGTH - filled;
        int percent = (int) Math.round(ratio * 100);

        return "§a" + "■".repeat(filled)
                + "§7" + "■".repeat(empty)
                + " §f" + percent + "%";
    }
}
