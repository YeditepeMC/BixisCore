package com.yeditepemc.bixiscore.command;

import com.yeditepemc.bixiscore.gui.LevelMenu;
import com.yeditepemc.bixiscore.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /level} — oyuncunun level ilerleme GUI'sini açar (CLAUDE.md 2.1).
 */
public class LevelCommand implements CommandExecutor {

    private final LevelMenu menu;

    public LevelCommand(LevelMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.comp("&cBu komut yalnızca oyuncular tarafından kullanılabilir."));
            return true;
        }
        menu.openMain(player);
        return true;
    }
}
