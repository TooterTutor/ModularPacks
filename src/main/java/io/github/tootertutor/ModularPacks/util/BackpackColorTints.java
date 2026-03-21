package io.github.tootertutor.ModularPacks.util;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.ModularPacks.item.CustomModelDataUtil;

/**
 * Utility for managing backpack color tints via minecraft:custom_model_data
 * colors list.
 * 
 * Stores a 6-element integer array:
 * - Indices 0-4: Editable color tints (RRGGBB format)
 * - Index 5: Backpack tier/rank (reserved for resource pack, read-only)
 * 
 * Example: {colors:[I;16711680,16750848,16252680,655104,329215,16187647]}
 * translates to: [Red, Orange, Yellow, DarkColor, Blue, Tier]
 */
public final class BackpackColorTints {

    private BackpackColorTints() {
    }

    /**
     * Get all color tints including tier as a 6-element integer array.
     * Returns default colors if not set: all white except tier.
     */
    public static int[] getColors(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return getDefaultColors();
        }

        List<Color> componentColors = CustomModelDataUtil.getCustomModelDataColors(meta);
        if (componentColors.isEmpty()) {
            return getDefaultColors();
        }

        int[] colors = getDefaultColors();
        int limit = Math.min(6, componentColors.size());
        for (int i = 0; i < limit; i++) {
            colors[i] = colorToRgb(componentColors.get(i));
        }
        return colors;
    }

    /**
     * Get the color tint at the given index (0-4).
     * Returns RRGGBB format as an integer.
     * Index 5 (tier) cannot be accessed via this method.
     */
    public static int getColorTint(ItemStack item, int colorIndex) {
        if (colorIndex < 0 || colorIndex >= 5) {
            return 0xFFFFFF; // White by default
        }

        int[] colors = getColors(item);
        return colors[colorIndex];
    }

    /**
     * Set a color tint at the given index (0-4).
     * colorValue should be RRGGBB format (0x000000 to 0xFFFFFF).
     * Index 5 (tier) cannot be set via this method.
     */
    public static void setColorTint(ItemStack item, int colorIndex,
            int colorValue) {
        if (colorIndex < 0 || colorIndex >= 5) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        int[] colors = getColors(item);
        colors[colorIndex] = colorValue;

        saveColors(meta, colors);
        item.setItemMeta(meta);
    }

    /**
     * Get the backpack tier/rank from the 6th color value (index 5).
     */
    public static int getBackpackTier(ItemStack item) {
        int[] colors = getColors(item);
        return colors[5];
    }

    /**
     * Set the backpack tier/rank in the 6th color value (index 5).
     */
    public static void setBackpackTier(ItemStack item, int tier) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        int[] colors = getColors(item);
        colors[5] = tier;

        saveColors(meta, colors);
        item.setItemMeta(meta);
    }

    /**
     * Set all 6 color values at once (0-4 editable colors + index 5 tier).
     */
    public static void setColors(ItemStack item, int[] colors) {
        if (colors == null || colors.length != 6) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        saveColors(meta, colors);
        item.setItemMeta(meta);
    }

    /**
     * Set the first 5 color tints from a DyeColor array, preserving tier.
     */
    public static void setEditableColors(ItemStack item,
            DyeColor[] dyeColors) {
        if (dyeColors == null || dyeColors.length < 5) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        int[] colors = getColors(item);

        // Set colors 0-4 from DyeColor mapping
        for (int i = 0; i < 5; i++) {
            colors[i] = dyeColorToRGB(dyeColors[i]);
        }
        // Index 5 (tier) is preserved

        saveColors(meta, colors);
        item.setItemMeta(meta);
    }

    /**
     * Initialize a backpack item with default colors and an optional tier.
     */
    public static void initializeColors(ItemStack item, int tier) {
        int[] colors = getDefaultColors();
        colors[5] = tier;
        setColors(item, colors);
    }

    /**
     * Get default colors: white for indices 0-4, and 0 for tier (index 5).
     */
    private static int[] getDefaultColors() {
        return new int[] { 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF,
                0xFFFFFF, 0 };
    }

    /**
     * Save colors array to ItemMeta's custom_model_data colors list.
     */
    private static void saveColors(ItemMeta meta, int[] colors) {
        List<Color> colorList = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            colorList.add(rgbToColor(colors[i]));
        }
        CustomModelDataUtil.setCustomModelDataColors(meta, colorList);
    }

    private static int colorToRgb(Color color) {
        if (color == null) {
            return 0xFFFFFF;
        }
        return (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    private static Color rgbToColor(int rgb) {
        int clamped = rgb & 0xFFFFFF;
        int red = (clamped >> 16) & 0xFF;
        int green = (clamped >> 8) & 0xFF;
        int blue = clamped & 0xFF;
        return Color.fromRGB(red, green, blue);
    }

    /**
     * Convert a DyeColor to RGB value (standard Minecraft dye colors).
     */
    private static int dyeColorToRGB(DyeColor color) {
        return switch (color) {
            case WHITE -> 0xFFFFFF;
            case ORANGE -> 0xFFB000;
            case MAGENTA -> 0xFF00FF;
            case LIGHT_BLUE -> 0x0080FF;
            case YELLOW -> 0xFFFF00;
            case LIME -> 0x00FF00;
            case PINK -> 0xFF80FF;
            case GRAY -> 0x808080;
            case LIGHT_GRAY -> 0xC0C0C0;
            case CYAN -> 0x00FFFF;
            case PURPLE -> 0x8000FF;
            case BLUE -> 0x0000FF;
            case BROWN -> 0x804000;
            case GREEN -> 0x008000;
            case RED -> 0xFF0000;
            case BLACK -> 0x000000;
        };
    }
}
