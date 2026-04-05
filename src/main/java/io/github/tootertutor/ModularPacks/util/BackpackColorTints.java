package io.github.tootertutor.ModularPacks.util;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.api.ModularPacksAPI;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
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

    private static final int FALLBACK_TIER_COLOR = 0xFFFFFF;

    private BackpackColorTints() {
    }

    /**
     * Get all color tints including tier as a 6-element integer array.
     * Returns backpack-type default colors when no overrides are present.
     */
    public static int[] getColors(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return getDefaultColors(getTierDefault(item));
        }

        List<Color> componentColors = CustomModelDataUtil.getCustomModelDataColors(meta);
        List<String> componentStrings = CustomModelDataUtil.getCustomModelDataStrings(meta);

        int tierDefault = getTierDefault(item);
        int[] colors = getDefaultColors(tierDefault);

        if (hasLegacyColorKeys(componentStrings) && !componentColors.isEmpty()
                && componentColors.size() == componentStrings.size()) {
            for (int i = 0; i < componentColors.size(); i++) {
                String key = componentStrings.get(i);
                int rgb = colorToRgb(componentColors.get(i));
                Integer colorIndex = parseColorKey(key);
                if (colorIndex != null) {
                    colors[colorIndex] = rgb;
                }
            }
            return colors;
        }

        if (componentColors.isEmpty()) {
            return colors;
        }

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
            return FALLBACK_TIER_COLOR;
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

        ColorState state = readState(meta);
        state.colors[colorIndex] = colorValue & 0xFFFFFF;

        saveState(meta, state);
        item.setItemMeta(meta);
    }

    /**
     * Remove a color tint at the given index (0-4) by restoring the default
     * value for that slot in the underlying custom_model_data colors list.
     */
    public static void clearColorTint(ItemStack item, int colorIndex) {
        if (colorIndex < 0 || colorIndex >= 5) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        ColorState state = readState(meta);
        state.colors[colorIndex] = null;

        saveState(meta, state);
        item.setItemMeta(meta);
    }

    /**
     * Get the backpack tier/rank from the 6th color value (index 5).
     */
    public static int getBackpackTier(ItemStack item) {
        return getTierDefault(item);
    }

    /**
     * Set the backpack tier/rank in the 6th color value (index 5).
     */
    public static void setBackpackTier(ItemStack item, int tier) {
        // Tier color is derived from backpack_type defaults and is not directly
        // settable.
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

        ColorState state = new ColorState();
        for (int i = 0; i < 5; i++) {
            state.colors[i] = colors[i] & 0xFFFFFF;
        }

        saveState(meta, state);
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

        ColorState state = readState(meta);

        for (int i = 0; i < 5; i++) {
            state.colors[i] = dyeColorToRGB(dyeColors[i]);
        }

        saveState(meta, state);
        item.setItemMeta(meta);
    }

    /**
     * Initialize a backpack item with default colors and an optional tier.
     */
    public static void initializeColors(ItemStack item, int tier) {
        int[] colors = getDefaultColors(getTierDefault(item));
        setColors(item, colors);
    }

    /**
     * Get default colors for all model groups and the tier slot from the backpack
     * type's configured default color.
     */
    private static int[] getDefaultColors(int tierDefault) {
        return new int[] { tierDefault, tierDefault, tierDefault, tierDefault,
                tierDefault, tierDefault };
    }

    /**
     * Save colors state to ItemMeta's custom_model_data colors list.
     *
     * Always writes the full six-slot color payload so CMD colors remain index-
     * stable regardless of edit order:
     * - index 0..4 => editable model groups, defaulting to tier/default when
     * cleared
     * - index 5 => derived tier/default color
     *
     * Existing non-color CMD strings, such as module flags, are preserved.
     * Legacy color:* strings are stripped during save.
     */
    private static void saveState(ItemMeta meta, ColorState state) {
        List<Color> colorList = new ArrayList<>();
        int tierDefault = getTierDefault(meta);

        for (int i = 0; i < 5; i++) {
            colorList.add(rgbToColor(state.colors[i] == null ? tierDefault : state.colors[i]));
        }

        colorList.add(rgbToColor(tierDefault));

        List<String> preservedStrings = stripLegacyColorKeys(CustomModelDataUtil.getCustomModelDataStrings(meta));
        CustomModelDataUtil.setCustomModelDataStrings(meta, preservedStrings);
        CustomModelDataUtil.setCustomModelDataColors(meta, colorList);
    }

    private static ColorState readState(ItemMeta meta) {
        ColorState state = new ColorState();
        List<Color> componentColors = CustomModelDataUtil.getCustomModelDataColors(meta);
        List<String> componentStrings = CustomModelDataUtil.getCustomModelDataStrings(meta);

        if (hasLegacyColorKeys(componentStrings) && !componentColors.isEmpty()
                && componentColors.size() == componentStrings.size()) {
            for (int i = 0; i < componentColors.size(); i++) {
                String key = componentStrings.get(i);
                int rgb = colorToRgb(componentColors.get(i));
                Integer colorIndex = parseColorKey(key);
                if (colorIndex != null && colorIndex < 5) {
                    state.colors[colorIndex] = rgb;
                }
            }
            return state;
        }

        int limit = Math.min(5, componentColors.size());
        for (int i = 0; i < limit; i++) {
            state.colors[i] = colorToRgb(componentColors.get(i));
        }
        return state;
    }

    private static List<String> stripLegacyColorKeys(List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return List.of();
        }

        List<String> filtered = new ArrayList<>(strings.size());
        for (String value : strings) {
            if (parseColorKey(value) == null) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private static boolean hasLegacyColorKeys(List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return false;
        }

        for (String value : strings) {
            if (parseColorKey(value) != null) {
                return true;
            }
        }
        return false;
    }

    private static Integer parseColorKey(String key) {
        if (key == null || !key.startsWith("color:")) {
            return null;
        }
        try {
            int colorIndex = Integer.parseInt(key.substring("color:".length()));
            if (colorIndex < 0 || colorIndex > 5) {
                return null;
            }
            return colorIndex;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class ColorState {
        private final Integer[] colors = new Integer[5];
    }

    private static int getTierDefault(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return FALLBACK_TIER_COLOR;
        }

        return getTierDefault(meta);
    }

    private static int getTierDefault(ItemMeta meta) {
        if (meta == null) {
            return FALLBACK_TIER_COLOR;
        }

        ModularPacksAPI api = ModularPacksAPI.getInstance();
        if (api == null || api.getPlugin() == null) {
            return FALLBACK_TIER_COLOR;
        }

        String backpackType = meta.getPersistentDataContainer()
                .get(api.getPlugin().keys().BACKPACK_TYPE, PersistentDataType.STRING);
        if (backpackType == null) {
            return FALLBACK_TIER_COLOR;
        }

        BackpackTypeDef def = api.getPlugin().cfg().findType(backpackType);
        if (def == null) {
            return FALLBACK_TIER_COLOR;
        }

        return def.defaultColor() & 0xFFFFFF;
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
