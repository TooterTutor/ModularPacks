package io.github.tootertutor.ModularPacks.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory holder for the hopper-based backpack color picker.
 */
public final class ColorPickerHolder implements InventoryHolder {

    private final UUID backpackId;
    private final BackpackMenuHolder backpackMenuHolder;

    public ColorPickerHolder(UUID backpackId, BackpackMenuHolder backpackMenuHolder) {
        this.backpackId = backpackId;
        this.backpackMenuHolder = backpackMenuHolder;
    }

    public UUID backpackId() {
        return backpackId;
    }

    public BackpackMenuHolder backpackMenuHolder() {
        return backpackMenuHolder;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}