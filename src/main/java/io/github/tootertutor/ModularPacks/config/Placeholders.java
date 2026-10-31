package io.github.tootertutor.ModularPacks.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class Placeholders {
    private Placeholders() {
    }

    public static List<String> expandLore(ModularPacksPlugin plugin, List<String> lore) {
        return expandLoreFor(plugin, null, lore);
    }

    public static List<String> expandBackpackLore(ModularPacksPlugin plugin, BackpackTypeDef type, UUID backpackId,
            List<String> lore) {
        return expandBackpackLoreFor(plugin, null, type, backpackId, lore);
    }

    public static List<String> expandBackpackLore(ModularPacksPlugin plugin,
            BackpackTypeDef type,
            UUID backpackId,
            BackpackData data,
            int totalSlots,
            List<String> lore) {
        return expandBackpackLoreFor(plugin, null, type, backpackId, data, totalSlots, lore);
    }

    public static List<String> expandLore(ModularPacksPlugin plugin, UpgradeDef def, List<String> lore) {
        return expandLoreFor(plugin, null, def, lore);
    }

    public static List<String> expandLore(ModularPacksPlugin plugin, UpgradeDef def, ItemStack moduleItem,
            List<String> lore) {
        return expandLoreFor(plugin, null, def, moduleItem, lore);
    }

    public static String expandText(ModularPacksPlugin plugin, UpgradeDef def, ItemStack moduleItem, String text) {
        return expandTextFor(plugin, null, def, moduleItem, text);
    }

    public static String expandLangText(ModularPacksPlugin plugin, UpgradeDef def, ItemStack moduleItem,
            String langKey, String fallback) {
        return expandLangTextFor(plugin, null, def, moduleItem, langKey, fallback);
    }

    public static List<String> expandLangList(ModularPacksPlugin plugin, UpgradeDef def, ItemStack moduleItem,
            String langKey, List<String> fallback) {
        return expandLangListFor(plugin, null, def, moduleItem, langKey, fallback);
    }

    private static final int MAX_EXPANSION_DEPTH = 6;
    private static final int MAX_EXPANDED_LINES = 250;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9._\\-]+)\\}");

    public static List<String> expandLoreFor(ModularPacksPlugin plugin, Player viewer, List<String> lore) {
        if (plugin == null)
            return lore == null ? List.of() : lore;

        Map<String, Replacement> overrides = new HashMap<>();
        // Back-compat: treat {moduleActions} as "primary" actions.
        overrides.put("moduleActions",
                Replacement.list(resolveActionList(plugin, viewer, "moduleActionsPrimary", "moduleActions")));

        return expandLines(plugin, viewer, null, null, lore, overrides);
    }

    public static List<String> expandBackpackLoreFor(ModularPacksPlugin plugin, Player viewer, BackpackTypeDef type,
            UUID backpackId,
            List<String> lore) {
        if (plugin == null)
            return lore == null ? List.of() : lore;

        if (type == null) {
            String typeId = backpackId == null ? null : plugin.repo().findBackpackType(backpackId);
            type = typeId == null ? null : plugin.cfg().findType(typeId);
        }

        int totalSlots = type == null ? 0 : (type.rows() * 9);

        BackpackData data = null;
        if (backpackId != null) {
            String effectiveType = type != null ? type.id() : plugin.repo().findBackpackType(backpackId);
            if (effectiveType == null || effectiveType.isBlank())
                effectiveType = "Unknown";
            data = plugin.repo().loadOrCreate(backpackId, effectiveType);
        }

        return expandBackpackLoreFor(plugin, viewer, type, backpackId, data, totalSlots, lore);
    }

    public static List<String> expandBackpackLoreFor(
            ModularPacksPlugin plugin, Player viewer,
            BackpackTypeDef type,
            UUID backpackId,
            BackpackData data,
            int totalSlots,
            List<String> lore) {
        if (plugin == null)
            return lore == null ? List.of() : lore;

        Map<String, Replacement> overrides = new HashMap<>();

        String backpackIdStr = backpackId == null ? "" : backpackId.toString();
        overrides.put("backpackId", Replacement.scalar(backpackIdStr));
        overrides.put("BackpackId", Replacement.scalar(backpackIdStr));

        if (type != null) {
            overrides.put("typeId", Replacement.scalar(type.id()));
            overrides.put("TypeId", Replacement.scalar(type.id()));
            overrides.put("rows", Replacement.scalar(Integer.toString(type.rows())));
            overrides.put("Rows", Replacement.scalar(Integer.toString(type.rows())));
            overrides.put("upgradeSlots", Replacement.scalar(Integer.toString(type.upgradeSlots())));
            overrides.put("UpgradeSlots", Replacement.scalar(Integer.toString(type.upgradeSlots())));
        }

        int usedSlots = 0;
        long itemCount = 0;
        int installedModules = 0;
        List<String> installedModuleLines = new ArrayList<>();

        if (data != null) {
            var storage = plugin.backpackStorage().load(data);

            int effectiveTotalSlots = totalSlots > 0 ? totalSlots : storage.size();

            int limit = Math.min(storage.size(), Math.max(0, effectiveTotalSlots));
            for (int i = 0; i < limit; i++) {
                var stored = storage.get(i);
                if (stored == null)
                    continue;
                usedSlots++;
                itemCount = Math.addExact(itemCount, stored.count());
            }

            installedModules = data.installedModules().size();

            // Build installed module display lines from stored snapshots (preferred)
            // or fall back to module UUID.
            Keys keys = plugin.keys();
            data.installedModules().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        UUID moduleId = e.getValue();
                        if (moduleId == null)
                            return;

                        String display = null;
                        byte[] snap = data.installedSnapshots().get(moduleId);
                        if (snap != null) {
                            ItemStack[] arr = ItemStackCodec.fromBytes(snap);
                            ItemStack moduleItem = (arr != null && arr.length > 0) ? arr[0] : null;
                            if (moduleItem != null && moduleItem.hasItemMeta()) {
                                ItemMeta meta = moduleItem.getItemMeta();
                                if (meta != null) {
                                    String moduleType = meta.getPersistentDataContainer().get(keys.MODULE_TYPE,
                                            PersistentDataType.STRING);
                                    if (moduleType != null) {
                                        UpgradeDef def = plugin.cfg().findUpgrade(moduleType);
                                        display = def != null ? def.displayName() : moduleType;
                                    }
                                }
                            }
                        }

                        if (display == null) {
                            String shortId = moduleId.toString();
                            display = shortId.length() > 8 ? shortId.substring(0, 8) : shortId;
                        }

                        installedModuleLines.add("&7- &f" + display);
                    });
        }

        int effectiveTotalSlots = totalSlots;
        if (effectiveTotalSlots <= 0 && type != null) {
            effectiveTotalSlots = type.rows() * 9;
        }
        if (effectiveTotalSlots < 0)
            effectiveTotalSlots = 0;

        overrides.put("totalSlots", Replacement.scalar(Integer.toString(effectiveTotalSlots)));
        overrides.put("TotalSlots", Replacement.scalar(Integer.toString(effectiveTotalSlots)));

        overrides.put("usedSlots", Replacement.scalar(Integer.toString(usedSlots)));
        overrides.put("UsedSlots", Replacement.scalar(Integer.toString(usedSlots)));
        overrides.put("itemCount", Replacement.scalar(Long.toString(itemCount)));
        overrides.put("ItemCount", Replacement.scalar(Long.toString(itemCount)));
        overrides.put("installedModuleCount", Replacement.scalar(Integer.toString(installedModules)));
        overrides.put("InstalledModuleCount", Replacement.scalar(Integer.toString(installedModules)));

        overrides.put("installedModules", Replacement.list(installedModuleLines));
        overrides.put("InstalledModules", Replacement.list(installedModuleLines));

        boolean empty = (usedSlots <= 0 && itemCount <= 0);
        List<String> tpl = empty ? plugin.lang().getList(viewer, "backpackContentsEmpty")
                : plugin.lang().getList(viewer, "backpackContents");
        if (tpl == null || tpl.isEmpty()) {
            tpl = empty
                    ? List.of("&7Contents: &8(Empty)")
                    : List.of("&7Contents: &f{usedSlots}&7/&f{totalSlots} &7slots", "&7Items: &f{itemCount}");
        }
        overrides.put("backpackContents", Replacement.list(tpl));

        return expandLines(plugin, viewer, null, null, lore, overrides);
    }

    public static List<String> expandLoreFor(ModularPacksPlugin plugin, Player viewer, UpgradeDef def,
            List<String> lore) {
        return expandLoreFor(plugin, viewer, def, null, lore);
    }

    public static List<String> expandLoreFor(ModularPacksPlugin plugin, Player viewer, UpgradeDef def,
            ItemStack moduleItem,
            List<String> lore) {
        if (plugin == null)
            return lore == null ? List.of() : lore;
        if (def == null)
            return expandLoreFor(plugin, viewer, lore);

        return expandLines(plugin, viewer, def, moduleItem, lore,
                moduleOverrides(plugin, viewer, def, moduleItem));
    }

    public static String expandTextFor(ModularPacksPlugin plugin, Player viewer, UpgradeDef def, ItemStack moduleItem,
            String text) {
        if (plugin == null || text == null)
            return text;

        return expandString(plugin, viewer, def, moduleItem, text,
                moduleOverrides(plugin, viewer, def, moduleItem));
    }

    /** Build one consistent set of values for both item names and lore. */
    private static Map<String, Replacement> moduleOverrides(ModularPacksPlugin plugin, Player viewer,
            UpgradeDef def, ItemStack moduleItem) {
        Map<String, Replacement> overrides = new HashMap<>();
        if (def != null) {
            overrides.put("moduleActions", Replacement.list(resolveActions(plugin, viewer, def)));
            String toggleState = resolveToggleState(plugin, viewer, def, moduleItem);
            overrides.put("toggleState", Replacement.scalar(toggleState));
            if (def.id() != null && def.id().equalsIgnoreCase("Jukebox")) {
                overrides.put("jukeboxMode", Replacement.scalar(resolveJukeboxMode(plugin, viewer, moduleItem)));
            } else {
                overrides.put("jukeboxMode", Replacement.scalar(""));
            }
            if (def.id() != null && def.id().equalsIgnoreCase("Feeding")) {
                overrides.put("feedingMode", Replacement.scalar(resolveFeedingMode(plugin, viewer, moduleItem)));
            } else {
                overrides.put("feedingMode", Replacement.scalar(""));
            }
            if (def.id() != null && (def.id().equalsIgnoreCase("Pump") || def.id().equalsIgnoreCase("ExpPump"))) {
                overrides.put("pumpMode", Replacement.scalar(resolvePumpMode(plugin, viewer, moduleItem, def.id())));
            } else {
                overrides.put("pumpMode", Replacement.scalar(""));
            }
            if (def.id() != null && def.id().equalsIgnoreCase("ExpPump")) {
                overrides.put("expPumpMending", Replacement.scalar(resolveExpPumpMending(plugin, viewer, moduleItem)));
                String targetLevel = Integer.toString(resolveExpPumpTargetLevel(plugin, viewer, moduleItem));
                overrides.put("level", Replacement.scalar(targetLevel));
                overrides.put("Level", Replacement.scalar(targetLevel));
                overrides.put("targetLevel", Replacement.scalar(targetLevel));
                overrides.put("TargetLevel", Replacement.scalar(targetLevel));
            } else {
                overrides.put("expPumpMending", Replacement.scalar(""));
                overrides.put("level", Replacement.scalar(""));
                overrides.put("Level", Replacement.scalar(""));
                overrides.put("targetLevel", Replacement.scalar(""));
                overrides.put("TargetLevel", Replacement.scalar(""));
            }
            if (def.id() != null && def.id().equalsIgnoreCase("Restock")) {
                String threshold = Integer.toString(resolveRestockThreshold(plugin, viewer, moduleItem, def));
                overrides.put("threshold", Replacement.scalar(threshold));
                overrides.put("Threshold", Replacement.scalar(threshold));
                overrides.put("restockThresholdValue", Replacement.scalar(threshold));
                overrides.put("RestockThresholdValue", Replacement.scalar(threshold));
            } else {
                overrides.put("threshold", Replacement.scalar(""));
                overrides.put("Threshold", Replacement.scalar(""));
                overrides.put("restockThresholdValue", Replacement.scalar(""));
                overrides.put("RestockThresholdValue", Replacement.scalar(""));
            }
            addUpgradeConfigScalars(plugin, viewer, def, overrides);
        } else {
            overrides.put("moduleActions",
                    Replacement.list(resolveActionList(plugin, viewer, "moduleActionsPrimary", "moduleActions")));
        }

        return overrides;
    }

    public static String expandLangTextFor(ModularPacksPlugin plugin, Player viewer, UpgradeDef def,
            ItemStack moduleItem,
            String langKey, String fallback) {
        if (plugin == null)
            return fallback;

        String text = plugin.lang().get(viewer, langKey, fallback);
        return expandTextFor(plugin, viewer, def, moduleItem, text);
    }

    public static List<String> expandLangListFor(ModularPacksPlugin plugin, Player viewer, UpgradeDef def,
            ItemStack moduleItem,
            String langKey, List<String> fallback) {
        if (plugin == null) {
            return fallback == null ? List.of() : fallback;
        }

        List<String> lines = plugin.lang().getList(viewer, langKey);
        if (!plugin.lang().has(viewer, langKey)) {
            lines = fallback == null ? List.of() : fallback;
        }

        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(expandTextFor(plugin, viewer, def, moduleItem, line));
        }
        return out;
    }

    // Only historical fallback relationships belong here. New module action lists
    // are discovered by their moduleActions<ModuleId> language key automatically.
    private static final Map<String, String> ACTION_FALLBACKS = Map.ofEntries(
            Map.entry("moduleActionsPrimary", "moduleActions"),
            Map.entry("moduleActionsSecondary", "moduleActionsPrimary"),
            Map.entry("moduleActionsPassive", "moduleActionsPrimary"),
            Map.entry("moduleActionsTank", "moduleActionsSecondary"),
            Map.entry("moduleActionsFluidTank", "moduleActionsTank"),
            Map.entry("moduleActionsExperienceTank", "moduleActionsTank"),
            Map.entry("moduleActionsFeeding", "moduleActionsSecondary"),
            Map.entry("moduleActionsJukebox", "moduleActionsSecondary"),
            Map.entry("moduleActionsAutocrafting", "moduleActionsPrimary"),
            Map.entry("moduleActionsPump", "moduleActionsSecondary"),
            Map.entry("moduleActionsExpPump", "moduleActionsSecondary"),
            Map.entry("moduleActionsQuiver", "moduleActionsSecondary"));

    private static List<String> resolveActions(ModularPacksPlugin plugin, Player viewer, UpgradeDef def) {
        if (plugin == null || def == null)
            return List.of();
        String fallback = def.secondaryAction() ? "moduleActionsSecondary"
                : def.screenType() == ScreenType.NONE ? "moduleActionsPassive" : "moduleActionsPrimary";
        String key = def.id() == null || def.id().isBlank() ? fallback : "moduleActions" + def.id();
        // Built-in IDs have always been case-insensitive. Preserve their canonical
        // language-key spelling while allowing new IDs to follow the same convention.
        key = ACTION_FALLBACKS.keySet().stream().filter(key::equalsIgnoreCase).findFirst().orElse(key);
        return resolveActionList(plugin, viewer, key, fallback);
    }

    private static List<String> resolveActionList(ModularPacksPlugin plugin, Player viewer,
            String key, String fallback) {
        if (plugin == null || plugin.lang() == null)
            return List.of();
        while (key != null) {
            // Presence, rather than non-emptiness, lets [] deliberately hide actions.
            if (plugin.lang().has(viewer, key))
                return plugin.lang().getList(viewer, key);
            if ("moduleActions".equals(key))
                return List.of();
            key = ACTION_FALLBACKS.getOrDefault(key, fallback);
        }
        return List.of();
    }

    private static String resolveToggleState(ModularPacksPlugin plugin, Player viewer, UpgradeDef def,
            ItemStack moduleItem) {
        if (plugin == null || def == null || !def.toggleable())
            return null;

        boolean enabled = true; // default-on if item missing/old

        if (moduleItem != null && moduleItem.hasItemMeta()) {
            ItemMeta meta = moduleItem.getItemMeta();
            if (meta != null) {
                Byte b = meta.getPersistentDataContainer().get(plugin.keys().MODULE_ENABLED, PersistentDataType.BYTE);
                if (b != null) {
                    enabled = (b == 1);
                }
            }
        }

        if (enabled) {
            return plugin.lang().get(viewer, "toggleState.enabled", "&7State: &aᴇɴᴀʙʟᴇᴅ");
        }
        return plugin.lang().get(viewer, "toggleState.disabled", "&7State: &cᴅɪѕᴀʙʟᴇᴅ");
    }

    private static String resolveJukeboxMode(ModularPacksPlugin plugin, Player viewer, ItemStack moduleItem) {
        if (plugin == null || moduleItem == null || !moduleItem.hasItemMeta())
            return "";

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return "";

        Keys keys = plugin.keys();
        String raw = meta.getPersistentDataContainer().get(keys.MODULE_JUKEBOX_MODE, PersistentDataType.STRING);
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);

        return switch (s) {
            case "SHUFFLE" -> plugin.lang().get(viewer, "jukeboxMode.shuffle", "&7Mode: &fShuffle");
            case "REPEAT_ONE" -> plugin.lang().get(viewer, "jukeboxMode.repeatOne", "&7Mode: &fRepeat 1");
            case "REPEAT_ALL" -> plugin.lang().get(viewer, "jukeboxMode.repeatAll", "&7Mode: &fRepeat All");
            default -> plugin.lang().get(viewer, "jukeboxMode.repeatAll", "&7Mode: &fRepeat All");
        };
    }

    private static String resolveFeedingMode(ModularPacksPlugin plugin, Player viewer, ItemStack moduleItem) {
        if (plugin == null || moduleItem == null || !moduleItem.hasItemMeta())
            return "";

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return "";

        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();

        String rawMode = pdc.get(keys.MODULE_FEEDING_SELECTION_MODE, PersistentDataType.STRING);
        String rawPref = pdc.get(keys.MODULE_FEEDING_PREFERENCE, PersistentDataType.STRING);

        if (rawMode == null || rawMode.isBlank()) {
            rawMode = plugin.cfg().getString("Upgrades.Feeding.SelectionMode", "BestCandidate");
        }
        if (rawPref == null || rawPref.isBlank()) {
            rawPref = plugin.cfg().getString("Upgrades.Feeding.Preference", "Nutrition");
        }

        String mode = rawMode.trim().toUpperCase(Locale.ROOT);
        String pref = rawPref.trim().toUpperCase(Locale.ROOT);

        boolean whitelist = mode.contains("WHITELIST") || mode.contains("PREFER_FIRST");
        boolean effects = pref.contains("EFFECT");

        if (!whitelist && !effects) {
            return plugin.lang().get(viewer, "feedingMode.candidateNutrition",
                    "&7Mode: &fBest Candidate: Prefer Nutrition");
        }
        if (!whitelist) {
            return plugin.lang().get(viewer, "feedingMode.candidateEffects",
                    "&7Mode: &fBest Candidate: Prefer Effects");
        }
        if (!effects) {
            return plugin.lang().get(viewer, "feedingMode.whitelistNutrition",
                    "&7Mode: &fFirst in Whitelist: Prefer Nutrition");
        }
        return plugin.lang().get(viewer, "feedingMode.whitelistEffects",
                "&7Mode: &fFirst in Whitelist: Prefer Effects");
    }

    private static String resolvePumpMode(ModularPacksPlugin plugin, Player viewer, ItemStack moduleItem,
            String upgradeId) {
        if (plugin == null)
            return "";

        String fallback = "Deposit";
        if (upgradeId != null) {
            fallback = plugin.cfg().getString("Upgrades." + upgradeId + ".Mode", "Deposit");
        }

        String raw = null;
        if (moduleItem != null && moduleItem.hasItemMeta()) {
            ItemMeta meta = moduleItem.getItemMeta();
            if (meta != null) {
                raw = meta.getPersistentDataContainer().get(plugin.keys().MODULE_PUMP_MODE, PersistentDataType.STRING);
            }
        }

        String source = raw == null || raw.isBlank() ? fallback : raw;
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (upgradeId != null && upgradeId.equalsIgnoreCase("ExpPump")
                && (normalized.contains("KEEP") || normalized.contains("LEVEL"))) {
            int targetLevel = plugin.cfg().getInt("Upgrades.ExpPump.TargetLevel", 30);
            if (moduleItem != null && moduleItem.hasItemMeta()) {
                ItemMeta meta = moduleItem.getItemMeta();
                if (meta != null) {
                    Integer stored = meta.getPersistentDataContainer().get(plugin.keys().MODULE_EXP_PUMP_TARGET_LEVEL,
                            PersistentDataType.INTEGER);
                    if (stored != null) {
                        targetLevel = stored.intValue();
                    }
                }
            }

            targetLevel = Math.max(0, Math.min(100, targetLevel));
            return plugin.lang().get(viewer, "pumpMode.keepLevel", "&7Mode: &fKeep Player at Level {level}")
                    .replace("{level}", Integer.toString(targetLevel));
        }
        if (normalized.contains("WITHDRAW") || normalized.contains("OUT")) {
            return plugin.lang().get(viewer, "pumpMode.withdraw", "&7Mode: &fWithdraw");
        }
        return plugin.lang().get(viewer, "pumpMode.deposit", "&7Mode: &fDeposit");
    }

    private static String resolveExpPumpMending(ModularPacksPlugin plugin, Player viewer, ItemStack moduleItem) {
        if (plugin == null)
            return "";

        boolean enabled = plugin.cfg().getBoolean("Upgrades.ExpPump.MendEquippedItems", false);
        if (moduleItem != null && moduleItem.hasItemMeta()) {
            ItemMeta meta = moduleItem.getItemMeta();
            if (meta != null) {
                Byte b = meta.getPersistentDataContainer().get(plugin.keys().MODULE_EXP_PUMP_MENDING,
                        PersistentDataType.BYTE);
                if (b != null) {
                    enabled = (b == 1);
                }
            }
        }

        if (enabled) {
            return plugin.lang().get(viewer, "expPumpMending.enabled", "&7Mending: &aEnabled");
        }
        return plugin.lang().get(viewer, "expPumpMending.disabled", "&7Mending: &cDisabled");
    }

    private static int resolveExpPumpTargetLevel(ModularPacksPlugin plugin, Player viewer, ItemStack moduleItem) {
        if (plugin == null)
            return 30;

        int targetLevel = plugin.cfg().getInt("Upgrades.ExpPump.TargetLevel", 30);
        if (moduleItem != null && moduleItem.hasItemMeta()) {
            ItemMeta meta = moduleItem.getItemMeta();
            if (meta != null) {
                Integer stored = meta.getPersistentDataContainer().get(plugin.keys().MODULE_EXP_PUMP_TARGET_LEVEL,
                        PersistentDataType.INTEGER);
                if (stored != null) {
                    targetLevel = stored.intValue();
                }
            }
        }

        return Math.max(0, Math.min(100, targetLevel));
    }

    private static int resolveRestockThreshold(ModularPacksPlugin plugin, Player viewer, ItemStack moduleItem,
            UpgradeDef def) {
        if (plugin == null)
            return 16;

        int fallback = 16;
        if (def != null && def.id() != null) {
            fallback = plugin.cfg().getInt("Upgrades." + def.id() + ".RestockThreshold", fallback);
        }
        fallback = Math.max(1, Math.min(64, fallback));

        if (moduleItem == null || !moduleItem.hasItemMeta())
            return fallback;

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return fallback;

        Keys keys = plugin.keys();
        String b64 = meta.getPersistentDataContainer().get(keys.MODULE_STATE_B64, PersistentDataType.STRING);
        if (b64 == null || b64.isBlank())
            return fallback;

        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
        if (bytes == null || bytes.length == 0)
            return fallback;

        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(bytes);
        } catch (Exception ex) {
            return fallback;
        }
        if (arr == null || arr.length == 0)
            return fallback;

        // Prefer merged state index 9 (whitelist[0..8] + threshold[9]).
        if (arr.length > 9 && ItemStacks.isNotAir(arr[9])) {
            int t = arr[9].getAmount();
            if (t > 0)
                return Math.max(1, Math.min(64, t));
        }

        // Back-compat (old hopper-only): slot 2 is the center.
        if (arr.length > 2 && ItemStacks.isNotAir(arr[2])) {
            int t = arr[2].getAmount();
            if (t > 0)
                return Math.max(1, Math.min(64, t));
        }

        return fallback;
    }

    private static void addUpgradeConfigScalars(ModularPacksPlugin plugin, Player viewer, UpgradeDef def,
            Map<String, Replacement> overrides) {
        if (plugin == null || def == null || def.id() == null)
            return;
        if (overrides == null)
            return;

        var sec = plugin.cfg().getConfigurationSection("Upgrades." + def.id());
        if (sec == null)
            return;

        for (String key : sec.getKeys(false)) {
            Object raw = sec.get(key);
            String formatted = formatScalar(raw);
            if (formatted == null)
                continue;

            overrides.put(key, Replacement.scalar(formatted));

            String lc = lowerCamel(key);
            if (lc != null && !lc.equals(key)) {
                // Avoid overriding lang template keys (e.g. `restockThreshold` in en_us.yml).
                if (plugin.lang() != null && plugin.lang().raw(viewer, lc) != null) {
                    continue;
                }
                overrides.putIfAbsent(lc, Replacement.scalar(formatted));
            }
        }
    }

    private static String lowerCamel(String key) {
        if (key == null || key.isBlank())
            return null;
        if (key.length() == 1)
            return key.toLowerCase();
        char c0 = key.charAt(0);
        if (!Character.isUpperCase(c0))
            return key;
        return Character.toLowerCase(c0) + key.substring(1);
    }

    private static String formatScalar(Object raw) {
        if (raw == null)
            return null;
        if (raw instanceof String s)
            return s;
        if (raw instanceof Boolean b)
            return b.toString();
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte)
            return raw.toString();
        if (raw instanceof Float f) {
            if (f.isNaN() || f.isInfinite())
                return null;
            float rounded = (float) Math.rint(f);
            if (Math.abs(f - rounded) < 1.0e-6)
                return Integer.toString((int) rounded);
            return stripTrailingZeros(Float.toString(f));
        }
        if (raw instanceof Double d) {
            if (d.isNaN() || d.isInfinite())
                return null;
            double rounded = Math.rint(d);
            if (Math.abs(d - rounded) < 1.0e-9)
                return Long.toString((long) rounded);
            return stripTrailingZeros(Double.toString(d));
        }
        return null;
    }

    private static String stripTrailingZeros(String s) {
        if (s == null)
            return null;
        int dot = s.indexOf('.');
        if (dot < 0)
            return s;
        int end = s.length();
        while (end > dot + 1 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end > dot && s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }

    private static List<String> expandLines(
            ModularPacksPlugin plugin, Player viewer,
            UpgradeDef def,
            ItemStack moduleItem,
            List<String> lore,
            Map<String, Replacement> overrides) {
        if (lore == null || lore.isEmpty())
            return List.of();

        List<String> current = new ArrayList<>();
        for (String line : lore) {
            if (line != null)
                current.add(line);
        }

        for (int depth = 0; depth < MAX_EXPANSION_DEPTH; depth++) {
            boolean changed = false;
            List<String> next = new ArrayList<>();

            for (String line : current) {
                if (line == null)
                    continue;

                List<String> expanded = expandLineToLines(plugin, viewer, def, moduleItem, line, overrides);
                if (expanded.isEmpty() || expanded.size() != 1 || !expanded.get(0).equals(line)) {
                    changed = true;
                }

                for (String out : expanded) {
                    if (out == null)
                        continue;
                    next.add(out);
                    if (next.size() >= MAX_EXPANDED_LINES)
                        break;
                }
                if (next.size() >= MAX_EXPANDED_LINES)
                    break;
            }

            current = next;
            if (!changed)
                break;
        }

        return current;
    }

    private static String expandString(
            ModularPacksPlugin plugin, Player viewer,
            UpgradeDef def,
            ItemStack moduleItem,
            String text,
            Map<String, Replacement> overrides) {
        if (text == null)
            return null;

        String current = text;
        for (int depth = 0; depth < MAX_EXPANSION_DEPTH; depth++) {
            Matcher m = PLACEHOLDER.matcher(current);
            boolean any = false;
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                any = true;
                String key = m.group(1);
                Replacement r = resolveReplacement(plugin, viewer, def, moduleItem, key, overrides);
                String repl = r == null ? null : r.asScalar();
                if (r != null && repl == null) {
                    // Known placeholder with no scalar value => remove.
                    repl = "";
                }
                if (repl == null) {
                    repl = m.group(0); // leave untouched
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(repl));
            }
            if (!any)
                return current;
            m.appendTail(sb);

            String next = sb.toString();
            if (next.equals(current))
                break;
            current = next;
        }
        return current;
    }

    private static List<String> expandLineToLines(
            ModularPacksPlugin plugin, Player viewer,
            UpgradeDef def,
            ItemStack moduleItem,
            String line,
            Map<String, Replacement> overrides) {
        List<String> lines = List.of(line);

        List<String> next = new ArrayList<>();

        for (String cur : lines) {
            if (cur == null)
                continue;

            Matcher m = PLACEHOLDER.matcher(cur);
            if (!m.find()) {
                next.add(cur);
                continue;
            }

            String key = m.group(1);
            Replacement r = resolveReplacement(plugin, viewer, def, moduleItem, key, overrides);
            if (r == null) {
                next.add(cur); // unknown placeholder
                continue;
            }

            if (r.isList()) {
                List<String> repls = r.list();
                if (repls == null || repls.isEmpty()) {
                    // hide pure placeholder lines (lets you disable lines via empty list)
                    if (!cur.trim().equals("{" + key + "}")) {
                        next.add(cur.replace("{" + key + "}", ""));
                    }
                    continue;
                }

                if (cur.trim().equals("{" + key + "}")) {
                    next.addAll(repls);
                    continue;
                }

                for (String repl : repls) {
                    next.add(cur.replace("{" + key + "}", repl == null ? "" : repl));
                    if (next.size() >= MAX_EXPANDED_LINES)
                        break;
                }
                continue;
            }

            String repl = r.scalar();
            if (repl == null) {
                // hide pure placeholder line
                if (!cur.trim().equals("{" + key + "}")) {
                    next.add(cur.replace("{" + key + "}", ""));
                }
                continue;
            }
            next.add(cur.replace("{" + key + "}", repl));
        }

        return next;
    }

    private static Replacement resolveReplacement(
            ModularPacksPlugin plugin, Player viewer,
            UpgradeDef def,
            ItemStack moduleItem,
            String key,
            Map<String, Replacement> overrides) {
        if (key == null || key.isBlank())
            return null;

        if (overrides != null) {
            Replacement r = overrides.get(key);
            if (r != null)
                return r;
        }

        if (plugin == null || plugin.lang() == null)
            return null;

        Object raw = plugin.lang().raw(viewer, key);
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o == null)
                    continue;
                out.add(o.toString());
            }
            return Replacement.list(out);
        }

        String formatted = formatScalar(raw);
        if (formatted != null)
            return Replacement.scalar(formatted);

        return null;
    }

    private record Replacement(String scalar, List<String> list) {
        static Replacement scalar(String s) {
            return new Replacement(s, null);
        }

        static Replacement list(List<String> l) {
            return new Replacement(null, l == null ? List.of() : l);
        }

        boolean isList() {
            return list != null;
        }

        String asScalar() {
            if (scalar != null)
                return scalar;
            if (list != null && !list.isEmpty())
                return list.get(0);
            return null;
        }
    }
}
