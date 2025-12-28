package io.github.tootertutor.SophiBackpacks.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ModuleSettingsHolder implements InventoryHolder {
    private final UUID backpackId;
    private final UUID moduleId;

    private Inventory inv;

    public ModuleSettingsHolder(UUID backpackId, UUID moduleId) {
        this.backpackId = backpackId;
        this.moduleId = moduleId;
    }

    public UUID backpackId() {
        return backpackId;
    }

    public UUID moduleId() {
        return moduleId;
    }

    public void setInventory(Inventory inv) {
        this.inv = inv;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
