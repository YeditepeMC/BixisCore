package com.yeditepemc.bixiscore.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * BixisCore level menülerini tanımlayan InventoryHolder.
 * Listener, bir envanterin bize ait olup olmadığını ve hangi ekran olduğunu
 * bu holder üzerinden anlar.
 */
public class LevelMenuHolder implements InventoryHolder {

    public enum Type {
        MAIN,
        LEADERBOARD,
        CALENDAR
    }

    private final Type type;
    private Inventory inventory;

    public LevelMenuHolder(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
