package io.github.tootertutor.ModularPacks.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory holder for Pump/ExpPump settings.
 */
public final class PumpSettingsHolder implements InventoryHolder {

    private final UUID backpackId;
    private final String backpackType;
    private final UUID moduleId;
    private final String moduleType;

    public PumpSettingsHolder(UUID backpackId, String backpackType, UUID moduleId, String moduleType) {
        this.backpackId = backpackId;
        this.backpackType = backpackType;
        this.moduleId = moduleId;
        this.moduleType = moduleType;
    }

    public UUID backpackId() {
        return backpackId;
    }

    public String backpackType() {
        return backpackType;
    }

    public UUID moduleId() {
        return moduleId;
    }

    public String moduleType() {
        return moduleType;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
