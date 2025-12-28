package io.github.tootertutor.SophiBackpacks.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.tootertutor.SophiBackpacks.config.ScreenType;

public final class ModuleScreenHolder implements InventoryHolder {

    private final UUID backpackId;
    private final UUID moduleId;
    private final ScreenType screenType;
    private final String backpackType;
    private Inventory inv;

    public ModuleScreenHolder(UUID backpackId, String backpackType, UUID moduleId, ScreenType screenType) {
        this.backpackId = backpackId;
        this.backpackType = backpackType;
        this.moduleId = moduleId;
        this.screenType = screenType;
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

    public ScreenType screenType() {
        return screenType;
    }

    public void setInventory(Inventory inv) {
        this.inv = inv;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
