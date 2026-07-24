package com.yeditepemc.bixiscore.manager;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.yeditepemc.bixiscore.database.DatabaseManager;
import com.yeditepemc.bixiscore.model.PlayerData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Level leaderboard'u — sıralama <b>toplam kazanılmış XP</b>'ye göredir, sadece
 * level'e göre değil. Böylece level 50 cap'ine ulaşan oyuncular arasında da
 * sıralama anlamlı kalır (CLAUDE.md 2.2 / 2.6).
 */
public class LeaderboardManager {

    /** Tek bir leaderboard satırı. Level, XP'den hesaplanır. */
    public record Entry(java.util.UUID uuid, String username, long xp, int level) {
    }

    private final DatabaseManager database;

    public LeaderboardManager(BixisCorePlugin plugin, DatabaseManager database) {
        this.database = database;
    }

    /**
     * En yüksek toplam XP'ye sahip ilk {@code limit} oyuncuyu asenkron getirir.
     */
    public CompletableFuture<List<Entry>> topByXp(int limit) {
        return database.executeQuery(
                "SELECT uuid, username, xp FROM players ORDER BY xp DESC LIMIT ?",
                rs -> {
                    List<Entry> list = new ArrayList<>();
                    while (rs.next()) {
                        long xp = rs.getLong("xp");
                        java.util.UUID uuid = java.util.UUID.fromString(rs.getString("uuid"));
                        list.add(new Entry(uuid, rs.getString("username"), xp, PlayerData.calculateLevel(xp)));
                    }
                    return list;
                },
                limit
        );
    }
}
