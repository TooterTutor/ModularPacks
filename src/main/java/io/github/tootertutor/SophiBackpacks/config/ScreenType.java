package io.github.tootertutor.SophiBackpacks.config;

public enum ScreenType {
    NONE,
    CRAFTING,
    SMITHING,
    SMELTING,
    BLASTING,
    SMOKING,
    ANVIL,
    STONECUTTER,
    DROPPER,
    HOPPER;

    public static ScreenType from(String raw) {
        if (raw == null)
            return NONE;
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "CRAFTING" -> CRAFTING;
            case "SMITHING" -> SMITHING;
            case "SMELTING" -> SMELTING;
            case "BLASTING" -> BLASTING;
            case "SMOKING" -> SMOKING;
            case "ANVIL" -> ANVIL;
            case "STONECUTTER" -> STONECUTTER;
            case "DROPPER" -> DROPPER;
            case "HOPPER" -> HOPPER;
            default -> NONE;
        };
    }
}
