package com.yeditepemc.bixiscore;

import com.yeditepemc.bixiscore.api.BixisCoreAPI;
import com.yeditepemc.bixiscore.command.LevelCommand;
import com.yeditepemc.bixiscore.database.DatabaseManager;
import com.yeditepemc.bixiscore.event.LevelUpEvent;
import com.yeditepemc.bixiscore.gui.LevelMenu;
import com.yeditepemc.bixiscore.gui.LevelMenuListener;
import com.yeditepemc.bixiscore.manager.LeaderboardManager;
import com.yeditepemc.bixiscore.manager.PlayerDataManager;
import com.yeditepemc.bixiscore.model.PlayerData;
import com.yeditepemc.bixiscore.placeholder.BixisCorePlaceholders;
import com.yeditepemc.bixiscore.reward.LevelRewards;
import com.yeditepemc.bixiscore.reward.RewardQueueManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * BixisCore ana plugin sınıfı.
 * YeditepeMC ağının merkezi veri ve API kütüphanesi.
 *
 * <p>Diğer pluginler {@code BixisCorePlugin.getAPI()} ile API'ye erişir.
 */
public final class BixisCorePlugin extends JavaPlugin implements Listener {

    private static BixisCorePlugin instance;
    private static Economy economy;

    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private BixisCoreAPI api;

    // Level GUI / ödül sistemi
    private LevelRewards levelRewards;
    private RewardQueueManager rewardQueueManager;
    private LeaderboardManager leaderboardManager;
    private LevelMenu levelMenu;

    @Override
    public void onEnable() {
        instance = this;

        // config.yml'i plugin klasörüne kopyala (yoksa)
        saveDefaultConfig();

        // 1) Veritabanı bağlantısı ve tablolar
        this.databaseManager = new DatabaseManager(this);
        databaseManager.connect();
        databaseManager.createTables();

        // 2) Managerler (singleton)
        this.playerDataManager = new PlayerDataManager(this, databaseManager);

        // 3) Vault ekonomi bağlantısı (softdepend — yoksa coin işlemleri devre dışı)
        if (setupEconomy()) {
            getLogger().info("Vault ekonomisine bağlanıldı: " + economy.getName());
        } else {
            getLogger().warning("Vault veya bir ekonomi eklentisi bulunamadı! Coin işlemleri devre dışı.");
        }

        // 4) Public API — hem static getter hem de Bukkit ServicesManager ile erişilebilir
        this.api = new BixisCoreAPI(this, playerDataManager);
        getServer().getServicesManager().register(
                BixisCoreAPI.class,
                api,
                this,
                ServicePriority.Normal
        );

        // 5) Event dinleyicileri
        getServer().getPluginManager().registerEvents(playerDataManager, this);
        getServer().getPluginManager().registerEvents(this, this); // LevelUpEvent (aşağıda)

        // 6) PlaceholderAPI entegrasyonu (softdepend — varsa placeholder'ları kaydet)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BixisCorePlaceholders(this).register();
            getLogger().info("PlaceholderAPI bulundu, placeholder'lar kaydedildi (%bixiscore_...%).");
        }

        // 7) Level ödül sistemi + /level GUI
        this.levelRewards = new LevelRewards(this);
        this.rewardQueueManager = new RewardQueueManager(this, databaseManager, levelRewards);
        this.leaderboardManager = new LeaderboardManager(this, databaseManager);
        this.levelMenu = new LevelMenu(this, levelRewards, rewardQueueManager, leaderboardManager);
        getServer().getPluginManager().registerEvents(rewardQueueManager, this);
        getServer().getPluginManager().registerEvents(
                new LevelMenuListener(this, levelMenu, rewardQueueManager, levelRewards), this);
        PluginCommand levelCmd = getCommand("level");
        if (levelCmd != null) {
            levelCmd.setExecutor(new LevelCommand(levelMenu));
        }

        // Sunucu yeniden yüklendiyse (reload) zaten online olan oyuncuları yükle
        getServer().getOnlinePlayers().forEach(p -> {
            playerDataManager.loadPlayer(p.getUniqueId(), p.getName());
            rewardQueueManager.loadClaimed(p.getUniqueId());
        });

        getLogger().info("BixisCore etkinleştirildi. (v" + getPluginMeta().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        // Cache'deki tüm oyuncuları senkron kaydet (scheduler artık çalışmıyor)
        if (playerDataManager != null) {
            playerDataManager.saveAllSync();
        }
        // Bağlantıyı kapat
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        getLogger().info("BixisCore devre dışı bırakıldı.");
        economy = null;
        instance = null;
    }

    /**
     * Vault üzerinden kayıtlı Economy sağlayıcısını bulur.
     *
     * @return bağlanıldıysa {@code true}
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Vault Economy sağlayıcısı. Vault yoksa {@code null} döner.
     */
    public static Economy getEconomy() {
        return economy;
    }

    /**
     * Plugin örneği (singleton).
     */
    public static BixisCorePlugin getInstance() {
        return instance;
    }

    /**
     * Diğer pluginlerin kullandığı public API.
     */
    public static BixisCoreAPI getAPI() {
        if (instance == null) {
            throw new IllegalStateException("BixisCore henüz etkin değil!");
        }
        return instance.api;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    // ==================================================================
    //  Seviye atlama bildirimi
    // ==================================================================

    @EventHandler
    public void onLevelUp(LevelUpEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("§6✦ §eSeviye atladın! §6Yeni seviyeniz: §f"
                + event.getNewLevel() + " §6✦");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    // ==================================================================
    //  /bixiscore komutu
    //  NOT: info/addcoin/reset alt komutları GEÇİCİ TEST amaçlıdır ve sürüm
    //  öncesi kaldırılacaktır. Ancak "addxp <oyuncu> <miktar>" KALICIDIR —
    //  SkyWarsReloaded konsoldan XP ödülü için bunu çağırır (bkz. CLAUDE.md).
    // ==================================================================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("bixiscore")) {
            return false;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // addxp / addcoin konsoldan da çalışır (hedef oyuncu belirtilir)
        // — SkyWarsReloaded bunları konsoldan tetikler
        if (sub.equals("addxp")) {
            handleAddXp(sender, args);
            return true;
        }
        if (sub.equals("addcoin")) {
            handleAddCoin(sender, args);
            return true;
        }

        // Diğer (geçici test) alt komutları yalnızca oyuncu kendisi için kullanır
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cBu alt komut yalnızca oyunculara özeldir. Konsoldan: "
                    + "§e/bixiscore addxp|addcoin <oyuncu> <miktar>");
            return true;
        }
        switch (sub) {
            case "info" -> handleInfo(player);
            case "reset" -> handleReset(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6BixisCore komutları:");
        sender.sendMessage("§e/bixiscore addxp <oyuncu> <miktar> §7- oyuncuya XP ekle (konsol/admin)");
        sender.sendMessage("§e/bixiscore addcoin <oyuncu> <miktar> §7- oyuncuya coin ekle (konsol/admin)");
        sender.sendMessage("§8— geçici test alt komutları (oyuncu) —");
        sender.sendMessage("§e/bixiscore info §7- coin, xp, level, streak bilgin");
        sender.sendMessage("§e/bixiscore addxp <miktar> §7- kendine XP ekle");
        sender.sendMessage("§e/bixiscore addcoin <miktar> §7- kendine coin ekle");
        sender.sendMessage("§e/bixiscore reset §7- verini sıfırla");
    }

    private void handleInfo(Player player) {
        PlayerData data = api.getPlayerData(player);
        if (data == null) {
            player.sendMessage("§cVerin henüz yüklenmedi, birazdan tekrar dene.");
            return;
        }
        String coinStr = economy != null
                ? String.valueOf((long) economy.getBalance(player))
                : "§8(Vault yok)";
        player.sendMessage("§6§l» BixisCore Bilgilerin");
        player.sendMessage("§7Coin: §e" + coinStr);
        player.sendMessage("§7XP: §e" + data.getXp()
                + " §7(sonraki seviyeye §e" + data.getXpToNextLevel() + " §7XP)");
        player.sendMessage("§7Seviye: §e" + data.getLevel() + "§7/§e" + PlayerData.MAX_LEVEL);
        player.sendMessage("§7Streak: §e" + data.getStreakDays() + " §7gün");
    }

    /**
     * İki biçimi destekler:
     * <ul>
     *   <li>{@code /bixiscore addxp <miktar>} — gönderen oyuncu kendine ekler</li>
     *   <li>{@code /bixiscore addxp <oyuncu> <miktar>} — konsol/admin, hedefe ekler
     *       (SkyWarsReloaded winCommands/killCommands buradan tetikler)</li>
     * </ul>
     */
    private void handleAddXp(CommandSender sender, String[] args) {
        Player target;
        Long amount;

        if (args.length >= 3) {
            // Hedefli biçim — konsoldan çalışır
            target = getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cOyuncu bulunamadı ya da çevrimiçi değil: §e" + args[1]);
                return;
            }
            amount = parseAmount(sender, args[2]);
        } else if (args.length == 2) {
            // Kendine ekleme — yalnızca oyuncu
            if (!(sender instanceof Player self)) {
                sender.sendMessage("§cKonsoldan kullanım: §e/bixiscore addxp <oyuncu> <miktar>");
                return;
            }
            target = self;
            amount = parseAmount(sender, args[1]);
        } else {
            sender.sendMessage("§cKullanım: §e/bixiscore addxp <oyuncu> <miktar>");
            return;
        }

        if (amount == null) {
            return;
        }

        boolean ok = api.addXP(target, amount);
        if (!ok) {
            sender.sendMessage("§cXP eklenemedi — §e" + target.getName()
                    + " §coyuncusunun verisi henüz yüklenmemiş olabilir.");
            return;
        }
        // Hedef zaten kendi "+XP" mesajını alır; farklı bir gönderene onay ver
        if (!(sender instanceof Player p) || !p.equals(target)) {
            sender.sendMessage("§a" + target.getName() + " §7oyuncusuna §e" + amount + " §aXP eklendi.");
        }
    }

    /**
     * İki biçimi destekler (addxp ile aynı desen):
     * <ul>
     *   <li>{@code /bixiscore addcoin <miktar>} — gönderen oyuncu kendine ekler</li>
     *   <li>{@code /bixiscore addcoin <oyuncu> <miktar>} — konsol/admin, hedefe ekler
     *       (SkyWarsReloaded winCommands/killCommands buradan tetikler)</li>
     * </ul>
     * Coin, {@link BixisCoreAPI#addCoins} üzerinden Vault'a (XConomy) yazılır; oyuncuya
     * gösterilen mesaj BixisCore kontrolünde ve Türkçedir ("+X coin kazandın!").
     */
    private void handleAddCoin(CommandSender sender, String[] args) {
        Player target;
        Long amount;

        if (args.length >= 3) {
            // Hedefli biçim — konsoldan çalışır
            target = getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cOyuncu bulunamadı ya da çevrimiçi değil: §e" + args[1]);
                return;
            }
            amount = parseAmount(sender, args[2]);
        } else if (args.length == 2) {
            // Kendine ekleme — yalnızca oyuncu
            if (!(sender instanceof Player self)) {
                sender.sendMessage("§cKonsoldan kullanım: §e/bixiscore addcoin <oyuncu> <miktar>");
                return;
            }
            target = self;
            amount = parseAmount(sender, args[1]);
        } else {
            sender.sendMessage("§cKullanım: §e/bixiscore addcoin <oyuncu> <miktar>");
            return;
        }

        if (amount == null) {
            return;
        }

        boolean ok = api.addCoins(target, amount);
        if (!ok) {
            sender.sendMessage("§cCoin eklenemedi — §e" + target.getName()
                    + " §c(ekonomi sistemi kullanılamıyor olabilir).");
            return;
        }
        // Hedef zaten kendi "+₺" mesajını alır; farklı bir gönderene onay ver
        if (!(sender instanceof Player p) || !p.equals(target)) {
            sender.sendMessage("§a" + target.getName() + " §7oyuncusuna §e" + amount + "₺ §aeklendi.");
        }
    }

    private void handleReset(Player player) {
        PlayerData data = api.getPlayerData(player);
        if (data == null) {
            player.sendMessage("§cVerin henüz yüklenmedi, birazdan tekrar dene.");
            return;
        }
        data.setXp(0);
        data.setStreakDays(0);
        data.setLastDaily(null);
        data.setLastWeekly(null);
        data.setLastMonthly(null);
        playerDataManager.savePlayer(data);

        // Coin artık Vault'ta — bakiyeyi sıfıra çek
        if (economy != null) {
            double balance = economy.getBalance(player);
            if (balance > 0) {
                economy.withdrawPlayer(player, balance);
            }
        }
        player.sendMessage("§aVerin sıfırlandı. §7(coin, xp, level, streak)");
    }

    /** Pozitif long ayrıştırır; hatalıysa gönderene Türkçe hata verir ve {@code null} döner. */
    private Long parseAmount(CommandSender sender, String raw) {
        try {
            long amount = Long.parseLong(raw.trim());
            if (amount <= 0) {
                sender.sendMessage("§cMiktar 0'dan büyük olmalı.");
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cGeçersiz sayı: §e" + raw);
            return null;
        }
    }
}
