package com.yeditepemc.bixiscore.database;

import com.yeditepemc.bixiscore.BixisCorePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Veritabanı katmanı. HikariCP connection pool'u üzerinden çalışır.
 * Varsayılan olarak SQLite kullanır; config {@code storage.type: mysql}
 * yapıldığında MySQL'e geçer.
 *
 * <p>Tüm public query/update metotları asenkrondur ve ana thread'i bloke etmez
 * (CLAUDE.md — Async veritabanı operasyonları zorunlu).
 */
public class DatabaseManager {

    /** Desteklenen veritabanı tipleri. */
    public enum Type {
        SQLITE,
        MYSQL
    }

    /** {@link ResultSet}'i bir değere dönüştüren, SQLException fırlatabilen fonksiyon. */
    @FunctionalInterface
    public interface ResultSetFunction<T> {
        T apply(ResultSet resultSet) throws SQLException;
    }

    private final BixisCorePlugin plugin;
    private Type type;
    private HikariDataSource dataSource;

    public DatabaseManager(BixisCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Bağlantı yönetimi
    // ------------------------------------------------------------------

    /**
     * Config'i okuyup connection pool'u kurar.
     */
    public void connect() {
        FileConfiguration config = plugin.getConfig();
        String rawType = config.getString("storage.type", "sqlite");
        this.type = "mysql".equalsIgnoreCase(rawType) ? Type.MYSQL : Type.SQLITE;

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("BixisCore-Pool");

        if (type == Type.MYSQL) {
            String host = config.getString("storage.mysql.host", "127.0.0.1");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "bixiscore");
            String username = config.getString("storage.mysql.username", "root");
            String password = config.getString("storage.mysql.password", "");
            int poolSize = config.getInt("storage.mysql.pool-size", 10);
            boolean useSsl = config.getBoolean("storage.mysql.use-ssl", false);

            // Sürücü sınıfını açıkça belirt — shade edilmiş mysql-connector-j'yi kullanır
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSsl
                    + "&allowPublicKeyRetrieval=" + (!useSsl)
                    + "&useUnicode=true&characterEncoding=UTF-8");
            hikari.setUsername(username);
            hikari.setPassword(password);
            hikari.setMaximumPoolSize(poolSize);
        } else {
            // SQLite — plugin klasöründe tek dosya
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("Plugin klasörü oluşturulamadı: " + dataFolder.getAbsolutePath());
            }
            String fileName = config.getString("storage.sqlite.file", "data.db");
            File dbFile = new File(dataFolder, fileName);

            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // SQLite tek yazıcıyı sever; yazma kilitlerini önlemek için havuz küçük tutulur.
            hikari.setMaximumPoolSize(1);
        }

        hikari.setConnectionTimeout(10_000L);
        this.dataSource = new HikariDataSource(hikari);
        plugin.getLogger().info("Veritabanı bağlantısı kuruldu (" + type + ").");
    }

    /**
     * Connection pool'u kapatır.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Veritabanı bağlantısı kapatıldı.");
        }
    }

    public Type getType() {
        return type;
    }

    public boolean isMySQL() {
        return type == Type.MYSQL;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ------------------------------------------------------------------
    //  Tablo oluşturma
    // ------------------------------------------------------------------

    /**
     * Gerekli tabloları (players) oluşturur. Bağlantı kurulur kurulmaz çağrılır;
     * sunucu açılışında bir kez, senkron çalışması sorun değildir.
     */
    public void createTables() {
        // level alanı saklanmaz — XP'den hesaplanır.
        // coin alanı saklanmaz — Vault ekonomisinde tutulur.
        // Not: players tablosunda AUTO_INCREMENT sütun yok (PK = uuid), bu yüzden
        //      SQLite/MySQL arasında çevrilecek AUTOINCREMENT ifadesi bulunmaz.
        //      Sütun tipleri (VARCHAR/BIGINT/INT) her iki motorda da geçerlidir.
        String columns =
                "  uuid VARCHAR(36) PRIMARY KEY," +
                "  username VARCHAR(16) NOT NULL," +
                "  xp BIGINT NOT NULL DEFAULT 0," +
                "  streak_days INT NOT NULL DEFAULT 0," +
                "  last_daily VARCHAR(32)," +
                "  last_weekly VARCHAR(32)," +
                "  last_monthly VARCHAR(32)";

        // MySQL için motor + utf8mb4; SQLite'ta tablo son eki kullanılmaz.
        String suffix = isMySQL() ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
        String playersSql = "CREATE TABLE IF NOT EXISTS players (" + columns + ")" + suffix;

        // Level ödül kuyruğu — BixisCore'a özel, tip-farkında (SQLite + MySQL).
        // status ileride sunucular-arası gecikmeli teslim için ayrılmıştır.
        String queueSql =
                "CREATE TABLE IF NOT EXISTS bixiscore_level_reward_queue (" +
                "  uuid VARCHAR(36) NOT NULL," +
                "  level INT NOT NULL," +
                "  status VARCHAR(16) NOT NULL DEFAULT 'PENDING'," +
                "  created_at VARCHAR(32)," +
                "  delivered_at VARCHAR(32)," +
                "  PRIMARY KEY (uuid, level)" +
                ")" + suffix;

        try (Connection conn = getConnection();
             PreparedStatement ps1 = conn.prepareStatement(playersSql);
             PreparedStatement ps2 = conn.prepareStatement(queueSql)) {
            ps1.executeUpdate();
            ps2.executeUpdate();
            plugin.getLogger().info("Veritabanı tabloları hazır.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Tablolar oluşturulamadı!", e);
        }
    }

    // ------------------------------------------------------------------
    //  Asenkron operasyonlar (Bukkit scheduler)
    // ------------------------------------------------------------------

    /**
     * Asenkron INSERT/UPDATE/DELETE. Etkilenen satır sayısını döner.
     */
    public CompletableFuture<Integer> executeUpdate(String sql, Object... params) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(executeUpdateSync(sql, params));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "executeUpdate hatası: " + sql, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Asenkron SELECT. {@link ResultSet} verilen fonksiyonla dönüştürülür.
     */
    public <T> CompletableFuture<T> executeQuery(String sql, ResultSetFunction<T> handler, Object... params) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                applyParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    future.complete(handler.apply(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "executeQuery hatası: " + sql, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Senkron update — yalnızca sunucu kapanışı gibi scheduler'ın çalışmadığı
     * durumlarda kullanılır (örn. onDisable içinde son kayıt).
     */
    public int executeUpdateSync(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            applyParams(ps, params);
            return ps.executeUpdate();
        }
    }

    private void applyParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
