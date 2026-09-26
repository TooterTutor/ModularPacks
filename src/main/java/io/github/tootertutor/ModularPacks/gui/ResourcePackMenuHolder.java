package io.github.tootertutor.ModularPacks.gui;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Holds staged selections until the player presses Apply. */
public final class ResourcePackMenuHolder implements InventoryHolder {

    private final BackpackMenuHolder source;
    private final LinkedHashSet<String> selected;

    public ResourcePackMenuHolder(BackpackMenuHolder source, Set<String> selected) {
        this.source = source;
        this.selected = new LinkedHashSet<>(selected);
    }

    public BackpackMenuHolder source() {
        return source;
    }

    public LinkedHashSet<String> selected() {
        return selected;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
