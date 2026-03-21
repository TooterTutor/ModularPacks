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

        if (customModelData == null) {
            meta.setCustomModelDataComponent(null);
            return;
        }

        CustomModelDataComponent component = getComponent(meta);
        if (component == null)
            return;

        component.setFloats(List.of((float) customModelData.intValue()));
        meta.setCustomModelDataComponent(component);
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

    public static void setCustomModelDataStrings(ItemMeta meta, List<String> strings) {
        if (meta == null) {
            return;
        }

        CustomModelDataComponent component = getComponent(meta);
        if (component == null) {
            return;
        }

        component.setStrings(strings == null ? List.of() : List.copyOf(strings));
        meta.setCustomModelDataComponent(component);
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

        CustomModelDataComponent component = getComponent(meta);
        if (component == null) {
            return;
        }

        component.setColors(colors == null ? List.of() : List.copyOf(colors));
        meta.setCustomModelDataComponent(component);
    }
}
