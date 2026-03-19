package io.github.tootertutor.ModularPacks.screens.core;

import org.bukkit.event.inventory.InventoryType;

import io.github.tootertutor.ModularPacks.config.ScreenType;

public record ScreenDefinition(
        ScreenType screenType,
        InventoryType inventoryType,
        Integer inventorySize,
        ScreenTitleResolver titleResolver) {

    public static ScreenDefinition typed(ScreenType screenType, InventoryType inventoryType,
            ScreenTitleResolver titleResolver) {
        return new ScreenDefinition(screenType, inventoryType, null, titleResolver);
    }

    public static ScreenDefinition sized(ScreenType screenType, int inventorySize,
            ScreenTitleResolver titleResolver) {
        return new ScreenDefinition(screenType, null, inventorySize, titleResolver);
    }

    public boolean usesInventoryType() {
        return inventoryType != null;
    }
}
