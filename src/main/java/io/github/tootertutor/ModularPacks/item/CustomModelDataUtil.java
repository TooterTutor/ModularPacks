package io.github.tootertutor.ModularPacks.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public final class CustomModelDataUtil {

    private CustomModelDataUtil() {
    }

    private static CustomModelDataComponent getComponent(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        return meta.getCustomModelDataComponent();
    }

    public static void setCustomModelData(ItemMeta meta, Integer customModelData) {
        if (meta == null)
            return;

        try {
            if (customModelData == null) {
                meta.setCustomModelDataComponent(null);
                return;
            }

            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            if (component != null) {
                component.setFloats(List.of((float) customModelData.intValue()));
                meta.setCustomModelDataComponent(component);
            }
        } catch (Exception e) {
            // Component doesn't exist yet or can't be modified - skip silently
        }
    }

    public static int getCustomModelData(ItemMeta meta) {
        CustomModelDataComponent component = getComponent(meta);
        if (component == null || component.getFloats().isEmpty()) {
            return 0;
        }
        return Math.round(component.getFloats().get(0));
    }

    public static List<String> getCustomModelDataStrings(ItemMeta meta) {
        CustomModelDataComponent component = getComponent(meta);
        if (component == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(component.getStrings());
    }

    public static List<Boolean> getCustomModelDataFlags(ItemMeta meta) {
        CustomModelDataComponent component = getComponent(meta);
        if (component == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(component.getFlags());
    }

    public static void setCustomModelDataStrings(ItemMeta meta, List<String> strings) {
        if (meta == null) {
            return;
        }

        try {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            if (component != null) {
                component.setStrings(strings == null ? List.of() : List.copyOf(strings));
                meta.setCustomModelDataComponent(component);
            }
        } catch (Exception e) {
            // Component doesn't exist yet or can't be modified - skip silently
        }
    }

    public static void setCustomModelDataFlags(ItemMeta meta, List<Boolean> flags) {
        if (meta == null) {
            return;
        }

        try {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            if (component != null) {
                component.setFlags(flags == null ? List.of() : List.copyOf(flags));
                meta.setCustomModelDataComponent(component);
            }
        } catch (Exception e) {
            // Component doesn't exist yet or can't be modified - skip silently
        }
    }

    public static List<Color> getCustomModelDataColors(ItemMeta meta) {
        CustomModelDataComponent component = getComponent(meta);
        if (component == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(component.getColors());
    }

    public static void setCustomModelDataColors(ItemMeta meta, List<Color> colors) {
        if (meta == null) {
            return;
        }

        try {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            if (component != null) {
                component.setColors(colors == null ? List.of() : List.copyOf(colors));
                meta.setCustomModelDataComponent(component);
            }
        } catch (Exception e) {
            // Component doesn't exist yet or can't be modified - skip silently
        }
    }

    public static void setModuleEncodingInteger(ItemMeta meta, int encodingInteger) {
        if (meta == null) {
            return;
        }

        try {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            if (component != null) {
                List<Float> floats = new ArrayList<>(component.getFloats());
                // Ensure floats list has at least 2 elements
                while (floats.size() < 2) {
                    floats.add(0f);
                }
                // Set floats[1] to the encoding integer
                floats.set(1, (float) encodingInteger);
                component.setFloats(floats);
                meta.setCustomModelDataComponent(component);
            }
        } catch (Exception e) {
            // Component doesn't exist yet or can't be modified - skip silently
        }
    }

    public static int getModuleEncodingInteger(ItemMeta meta) {
        CustomModelDataComponent component = getComponent(meta);
        if (component == null || component.getFloats().size() < 2) {
            return 0;
        }
        return Math.round(component.getFloats().get(1));
    }
}