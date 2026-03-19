package io.github.tootertutor.ModularPacks.screens.core;

import java.util.Locale;

import io.github.tootertutor.ModularPacks.config.ScreenType;

public final class DefaultScreenTypeResolver {

    private DefaultScreenTypeResolver() {
    }

    public static ScreenType deriveFromUpgradeId(String upgradeId) {
        if (upgradeId == null)
            return ScreenType.NONE;

        ScreenType declared = BuiltInModuleDeclarations.findPrimaryScreenType(upgradeId);
        if (declared != ScreenType.NONE)
            return declared;

        String key = upgradeId.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "generic" -> ScreenType.GENERIC;
            case "crafting" -> ScreenType.CRAFTING;
            case "smithing" -> ScreenType.SMITHING;
            case "stonecutter" -> ScreenType.STONECUTTER;
            case "anvil" -> ScreenType.ANVIL;
            case "smelting" -> ScreenType.SMELTING;
            case "blasting" -> ScreenType.BLASTING;
            case "smoking" -> ScreenType.SMOKING;
            case "restock", "feeding", "void", "magnet", "jukebox" -> ScreenType.DROPPER;
            default -> ScreenType.NONE;
        };
    }

    public static ScreenType fromConfigOrDerived(String configuredScreenType, String upgradeId) {
        ScreenType parsed = ScreenType.from(configuredScreenType);
        if (parsed != ScreenType.NONE)
            return parsed;

        return deriveFromUpgradeId(upgradeId);
    }
}
