package io.github.tootertutor.ModularPacks.screens.declarations;

import org.bukkit.event.inventory.InventoryType;

import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.screens.core.AbstractModuleDeclaration;
import io.github.tootertutor.ModularPacks.screens.core.ModuleScreenDeclaration;
import io.github.tootertutor.ModularPacks.screens.core.ScreenDefinition;

public final class VoidModuleDeclaration extends AbstractModuleDeclaration {

    public VoidModuleDeclaration() {
        super(
                "Void",
                "Void Module",
                ScreenType.DROPPER,
                new ModuleScreenDeclaration(
                        ScreenType.DROPPER,
                        ScreenDefinition.typed(ScreenType.DROPPER, InventoryType.DROPPER,
                                (screenType, moduleType, filterMode) -> "Void Whitelist")));
    }
}
