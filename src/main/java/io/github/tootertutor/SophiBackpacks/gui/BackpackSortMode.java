package io.github.tootertutor.SophiBackpacks.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public enum BackpackSortMode {
    REGISTRY("Registry"),
    CREATIVE_MENU("Creative Menu"),
    ALPHABETICALLY("Alphabetically"),
    COUNT("Count"),
    TAGS("Tags");

    private final String displayName;

    BackpackSortMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public BackpackSortMode next() {
        BackpackSortMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Comparator<ItemStack> comparator(SophiBackpacksPlugin plugin, BackpackSortMode mode) {
        Comparator<ItemStack> byRegistry = Comparator
                .comparing((ItemStack it) -> registryKey(it), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(it -> it.getType().name(), String.CASE_INSENSITIVE_ORDER);

        return switch (mode) {
            case REGISTRY -> byRegistry;
            case CREATIVE_MENU -> Comparator
                    .comparing((ItemStack it) -> creativeCategoryKey(it.getType()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byRegistry);
            case ALPHABETICALLY -> Comparator
                    .comparing((ItemStack it) -> displayNameKey(it), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byRegistry);
            case COUNT -> Comparator
                    .comparingInt((ItemStack it) -> it.getAmount()).reversed()
                    .thenComparing((ItemStack it) -> displayNameKey(it), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byRegistry);
            case TAGS -> Comparator
                    .comparing((ItemStack it) -> firstTagKey(it.getType()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byRegistry);
        };
    }

    private static String registryKey(ItemStack it) {
        if (it == null)
            return "";
        try {
            var key = it.getType().getKey();
            return key == null ? "" : key.toString();
        } catch (Exception ignored) {
            return it.getType().name();
        }
    }

    private static String displayNameKey(ItemStack it) {
        if (it == null)
            return "";
        ItemMeta meta = it.getItemMeta();
        if (meta == null)
            return it.getType().name();

        try {
            Component dn = meta.displayName();
            if (dn == null)
                return it.getType().name();
            String plain = PlainTextComponentSerializer.plainText().serialize(dn);
            if (plain == null || plain.isBlank())
                return it.getType().name();
            return plain;
        } catch (Exception ignored) {
            return it.getType().name();
        }
    }

    private static volatile boolean creativeChecked;
    private static volatile Method materialGetCreativeCategory;

    private static String creativeCategoryKey(Material material) {
        if (material == null)
            return "";

        ensureCreativeInit();
        if (materialGetCreativeCategory == null)
            return "";

        try {
            Object cat = materialGetCreativeCategory.invoke(material);
            return cat == null ? "" : cat.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void ensureCreativeInit() {
        if (creativeChecked)
            return;
        creativeChecked = true;

        try {
            materialGetCreativeCategory = Material.class.getMethod("getCreativeCategory");
        } catch (NoSuchMethodException ignored) {
            materialGetCreativeCategory = null;
        }
    }

    private static volatile boolean tagsInit;
    private static volatile List<Tag<Material>> materialTags;
    private static final Map<Material, String> materialToFirstTagKey = new ConcurrentHashMap<>();

    private static String firstTagKey(Material material) {
        if (material == null)
            return "zzzz";

        ensureTagsInit();
        return materialToFirstTagKey.computeIfAbsent(material, m -> {
            String best = null;

            for (Tag<Material> tag : materialTags) {
                boolean tagged;
                try {
                    tagged = tag.isTagged(m);
                } catch (Throwable ex) {
                    continue;
                }
                if (!tagged)
                    continue;

                String k;
                try {
                    k = tag.getKey().toString();
                } catch (Throwable ex) {
                    continue;
                }
                if (best == null || String.CASE_INSENSITIVE_ORDER.compare(k, best) < 0) {
                    best = k;
                }
            }

            return best == null ? "zzzz" : best;
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void ensureTagsInit() {
        if (tagsInit)
            return;
        tagsInit = true;

        List<Tag<Material>> out = new ArrayList<>();
        for (Field f : Tag.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()))
                continue;
            if (!Tag.class.isAssignableFrom(f.getType()))
                continue;

            Object v;
            try {
                v = f.get(null);
            } catch (IllegalAccessException ignored) {
                continue;
            }
            if (!(v instanceof Tag tag))
                continue;

            boolean looksLikeMaterialTag = false;
            try {
                for (Object val : tag.getValues()) {
                    if (val instanceof Material) {
                        looksLikeMaterialTag = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }

            if (!looksLikeMaterialTag)
                continue;

            out.add((Tag<Material>) tag);
        }

        materialTags = List.copyOf(out);
    }
}

