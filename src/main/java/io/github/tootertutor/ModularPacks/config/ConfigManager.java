package io.github.tootertutor.ModularPacks.config;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.screens.core.DefaultScreenTypeResolver;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class ConfigManager {

    private final ModularPacksPlugin plugin;

    // GUI nav materials (from config.yml)
    private boolean resizeGui = false;
    private Material navPageButtons = Material.ARROW;
    private Material navBorderFiller = Material.GRAY_STAINED_GLASS_PANE;
    private Material unlockedUpgradeSlotMaterial = Material.WHITE_STAINED_GLASS_PANE;
    private Material lockedUpgradeSlotMaterial = Material.IRON_BARS;

    // Backpack sounds
    private Sound backpackOpenSound = Sound.BLOCK_CHEST_OPEN;
    private Sound backpackCloseSound = Sound.BLOCK_CHEST_CLOSE;

    // Debug
    private boolean debugClickLog = false;

    // Update checker settings
    private boolean updateCheckerEnabled = true;
    private boolean updateCheckerShowChangeLog = true;
    private boolean updateCheckerCheckOnStartup = true;
    private boolean updateCheckerPeriodicCheck = true;
    private int updateCheckerIntervalHours = 24;
    private String updateCheckerNotifyPermission = "modularpacks.update.notify";
    private String updateCheckerReleaseApiUrl = "https://api.github.com/repos/tootertutor/ModularPacks/releases/latest";

    // Container rules
    private boolean allowShulkerBoxes = false;
    private boolean allowBundles = false;

    // Item types that cannot be inserted into backpacks (e.g. by Magnet)
    private Set<Material> backpackInsertBlacklist = Set.of();

    // Placeable backpacks settings
    private boolean placeableEnabled = true;
    private boolean dropPlacedBackpacksOnExplosion = false;
    private double placedBackpackRenderOffsetX = 0.5;
    private double placedBackpackRenderOffsetY = 0.4;
    private double placedBackpackRenderOffsetZ = 0.5;
    private double placedBackpackRenderRotationX = 0.0;
    private double placedBackpackRenderRotationY = 0.0;
    private double placedBackpackRenderRotationZ = 0.0;
    private double placedBackpackRenderScaleX = 1.0;
    private double placedBackpackRenderScaleY = 1.0;
    private double placedBackpackRenderScaleZ = 1.0;

    // Shared backpacks settings
    private boolean sharedBackpacksEnabled = true;
    private int maxSharedUsers = 5;

    // Single shared NamespacedKey for all GUI menu items; value identifies the type
    private NamespacedKey guiItemKey;

    // Backpack types by name
    private final Map<String, BackpackTypeDef> types = new HashMap<>();

    // Upgrades by ID
    private final Map<String, UpgradeDef> upgrades = new HashMap<>();

    public ConfigManager(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        this.types.clear();
        this.upgrades.clear();

        resizeGui = cfg.getBoolean("modularpacks.ResizeGUI", false);
        debugClickLog = cfg.getBoolean("modularpacks.Debug.ClickLog", false);

        String updateRoot = resolveUpdateCheckerRoot(cfg);
        updateCheckerEnabled = cfg.getBoolean(updateRoot + ".Enabled", true);
        updateCheckerShowChangeLog = cfg.getBoolean(updateRoot + ".ShowChangeLog", true);
        updateCheckerCheckOnStartup = cfg.getBoolean(updateRoot + ".CheckOnStartup", true);
        updateCheckerPeriodicCheck = cfg.getBoolean(updateRoot + ".PeriodicCheck", true);
        updateCheckerIntervalHours = Math.max(1, cfg.getInt(updateRoot + ".CheckIntervalHours", 24));
        updateCheckerNotifyPermission = cfg.getString(updateRoot + ".NotifyPermission", "modularpacks.update.notify");
        if (updateCheckerNotifyPermission == null || updateCheckerNotifyPermission.isBlank()) {
            updateCheckerNotifyPermission = "modularpacks.update.notify";
        }

        updateCheckerReleaseApiUrl = cfg.getString(updateRoot + ".ReleaseApiUrl",
                "https://api.github.com/repos/tootertutor/ModularPacks/releases/latest");
        if (updateCheckerReleaseApiUrl == null || updateCheckerReleaseApiUrl.isBlank()) {
            updateCheckerReleaseApiUrl = "https://api.github.com/repos/tootertutor/ModularPacks/releases/latest";
        }

        allowShulkerBoxes = cfg.getBoolean("modularpacks.AllowShulkerBoxes", false);
        allowBundles = cfg.getBoolean("modularpacks.AllowBundles", false);

        backpackOpenSound = parseSound(cfg.getString("modularpacks.BackpackOpenSound", "CHEST_OPEN"),
                Sound.BLOCK_CHEST_OPEN);
        backpackCloseSound = parseSound(cfg.getString("modularpacks.BackpackCloseSound", "CHEST_CLOSE"),
                Sound.BLOCK_CHEST_CLOSE);

        navPageButtons = mat(cfg.getString("modularpacks.NavPageButtons", "ARROW"), Material.ARROW);

        navBorderFiller = mat(cfg.getString("modularpacks.NavBorderFiller", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);

        unlockedUpgradeSlotMaterial = mat(
                cfg.getString("modularpacks.UnlockedUpgradeSlotMaterial", "WHITE_STAINED_GLASS_PANE"),
                Material.WHITE_STAINED_GLASS_PANE);
        lockedUpgradeSlotMaterial = mat(
                cfg.getString("modularpacks.LockedUpgradeSlotMaterial", "IRON_BARS"),
                Material.IRON_BARS);

        // Global insert blacklist (Magnet respects this; other insertion paths may as
        // well)
        Set<Material> bl = new HashSet<>();
        for (String raw : cfg.getStringList("modularpacks.BackpackInsertBlacklist")) {
            Material m = parseMaterial(raw);
            if (m != null)
                bl.add(m);
        }
        backpackInsertBlacklist = Collections.unmodifiableSet(bl);

        // Placeable backpacks settings
        placeableEnabled = cfg.getBoolean("modularpacks.Placeable", true);
        dropPlacedBackpacksOnExplosion = cfg.getBoolean("modularpacks.DropPlacedBackpacksOnExplosion", false);
        placedBackpackRenderOffsetX = cfg.getDouble("modularpacks.PlacedBackpackRender.Offset.X", 0.5);
        placedBackpackRenderOffsetY = cfg.getDouble("modularpacks.PlacedBackpackRender.Offset.Y", 0.2);
        placedBackpackRenderOffsetZ = cfg.getDouble("modularpacks.PlacedBackpackRender.Offset.Z", 0.5);
        placedBackpackRenderRotationX = cfg.getDouble("modularpacks.PlacedBackpackRender.Rotation.X", 0.0);
        placedBackpackRenderRotationY = cfg.getDouble("modularpacks.PlacedBackpackRender.Rotation.Y", 0.0);
        placedBackpackRenderRotationZ = cfg.getDouble("modularpacks.PlacedBackpackRender.Rotation.Z", 0.0);
        placedBackpackRenderScaleX = cfg.getDouble("modularpacks.PlacedBackpackRender.Scale.X", 1.0);
        placedBackpackRenderScaleY = cfg.getDouble("modularpacks.PlacedBackpackRender.Scale.Y", 1.0);
        placedBackpackRenderScaleZ = cfg.getDouble("modularpacks.PlacedBackpackRender.Scale.Z", 1.0);

        // Shared backpacks settings
        sharedBackpacksEnabled = cfg.getBoolean("modularpacks.SharedBackpacks.Enabled", true);
        maxSharedUsers = Math.max(1, cfg.getInt("modularpacks.SharedBackpacks.MaxSharedUsers", 5)); // Clamp to min 1

        cfg.getString("modularpacks.PDCNamespace", "modularpacks");
        guiItemKey = new NamespacedKey(plugin, "gui-item");

        // Backpack types
        ConfigurationSection typesSec = cfg.getConfigurationSection("BackpackTypes");
        if (typesSec != null) {
            for (String key : typesSec.getKeys(false)) {
                ConfigurationSection s = typesSec.getConfigurationSection(key);
                if (s == null)
                    continue;

                int rows = Math.max(1, Math.min(100, s.getInt("Rows", 2))); // Clamp 1-100
                int upgradeSlots = Math.max(0, s.getInt("UpgradeSlots", 0)); // Clamp >= 0
                String displayName = firstString(s, "DisplayName", key);
                List<String> lore = firstStringList(s, "Lore");
                int customModelData = firstInt(s, "CustomModelData", 0);
                int defaultColor = parseDefaultColor(s, 0xFFFFFF);
                String skullData = firstString(s, "SkullData", null);
                if (skullData != null) {
                    skullData = skullData.trim();
                    if (skullData.isEmpty())
                        skullData = null;
                }

                Material output = mat(
                        firstString(s, "OutputMaterial", s.getString("CraftingRecipe.OutputMaterial", "PLAYER_HEAD")),
                        Material.PLAYER_HEAD);

                types.put(key.toLowerCase(Locale.ROOT),
                        new BackpackTypeDef(key, displayName, rows, upgradeSlots, output, lore, customModelData,
                                skullData, defaultColor));
            }
        }

        // Upgrades/modules
        ConfigurationSection upSec = cfg.getConfigurationSection("Upgrades");
        if (upSec != null) {
            for (String id : upSec.getKeys(false)) {
                ConfigurationSection s = upSec.getConfigurationSection(id);
                if (s == null)
                    continue;

                boolean enabled = s.getBoolean("Enabled", true);
                boolean toggleable = s.getBoolean("Toggleable", false);
                boolean secondaryAction = s.getBoolean("SecondaryAction", false);

                String displayName = s.getString("DisplayName", id);
                String matName = s.getString("OutputMaterial", s.getString("CraftingRecipe.OutputMaterial", "PAPER"));
                Material material = mat(matName, Material.PAPER);

                List<String> lore = s.getStringList("Lore");
                int customModelData = s.getInt("CustomModelData", 0);
                boolean glint = s.getBoolean("Glint", s.getBoolean("CraftingRecipe.Glint", false));

                // Derive screen types from module ID to avoid config confusion.
                ScreenType screenType = DefaultScreenTypeResolver.deriveFromUpgradeId(id);

                upgrades.put(id.toLowerCase(Locale.ROOT),
                        new UpgradeDef(id, displayName, material, lore, customModelData, glint, enabled, toggleable,
                                secondaryAction, screenType));
            }
        }

        // Load external module definitions from other plugins
        loadExternalModuleDefinitions();
    }

    /**
     * Scan all loaded plugins for module definitions and register them.
     * External plugins can define modules in their config.yml under
     * "modularpacks-modules".
     */
    private void loadExternalModuleDefinitions() {
        int loadedCount = 0;
        for (Plugin externalPlugin : Bukkit.getPluginManager().getPlugins()) {
            // Skip ModularPacks itself
            if (externalPlugin.getName().equals("ModularPacks")) {
                continue;
            }

            // Only inspect enabled plugins, and guard against plugins whose config
            // systems are not fully initialized yet.
            if (!externalPlugin.isEnabled()) {
                continue;
            }

            FileConfiguration pluginConfig;
            try {
                pluginConfig = externalPlugin.getConfig();
            } catch (Throwable t) {
                this.plugin.getLogger().fine("[ModularPacks] Skipping external module scan for "
                        + externalPlugin.getName() + " (config unavailable during startup): " + t.getMessage());
                continue;
            }

            if (pluginConfig == null) {
                continue;
            }

            ConfigurationSection modulesSection = pluginConfig.getConfigurationSection("modularpacks-modules");
            if (modulesSection == null) {
                continue;
            }

            externalPlugin.getLogger().info("[ModularPacks] Discovering module definitions...");

            for (String id : modulesSection.getKeys(false)) {
                ConfigurationSection s = modulesSection.getConfigurationSection(id);
                if (s == null)
                    continue;

                // Skip if already registered (config.yml takes precedence)
                String key = id.toLowerCase(Locale.ROOT);
                if (upgrades.containsKey(key)) {
                    externalPlugin.getLogger().warning(
                            "[ModularPacks] Module '" + id
                                    + "' already registered, skipping (config.yml has priority)");
                    continue;
                }

                try {
                    boolean enabled = s.getBoolean("Enabled", true);
                    boolean toggleable = s.getBoolean("Toggleable", false);
                    boolean secondaryAction = s.getBoolean("SecondaryAction", false);

                    String displayName = s.getString("DisplayName", id);
                    String matName = s.getString("OutputMaterial",
                            s.getString("CraftingRecipe.OutputMaterial", "PAPER"));
                    Material material = mat(matName, Material.PAPER);

                    List<String> lore = s.getStringList("Lore");
                    int customModelData = s.getInt("CustomModelData", 0);
                    boolean glint = s.getBoolean("Glint", s.getBoolean("CraftingRecipe.Glint", false));

                    // Get screen type from config or derive from ID
                    String screenTypeStr = s.getString("ScreenType");
                    ScreenType screenType = DefaultScreenTypeResolver.fromConfigOrDerived(screenTypeStr, id);

                    UpgradeDef upgradeDef = new UpgradeDef(id, displayName, material, lore, customModelData, glint,
                            enabled, toggleable, secondaryAction, screenType);
                    upgrades.put(key, upgradeDef);
                    loadedCount++;
                    externalPlugin.getLogger().info(
                            "[ModularPacks] Registered module definition: " + id + " (" + externalPlugin.getName()
                                    + ")");
                } catch (Exception e) {
                    externalPlugin.getLogger().warning("[ModularPacks] Failed to load module definition '" + id + "': "
                            + e.getMessage());
                }
            }
        }

        if (loadedCount > 0) {
            this.plugin.getLogger().info("Loaded " + loadedCount + " external module definition(s)");
        }
    }

    /**
     * Get the shared NamespacedKey used on all GUI menu items.
     * The stored value identifies the item type (e.g. "nav-border-filler",
     * "nav-page-button").
     */
    public NamespacedKey getGuiItemKey() {
        return guiItemKey;
    }

    public boolean resizeGui() {
        return resizeGui;
    }

    public boolean debugClickLog() {
        return debugClickLog;
    }

    public boolean updateCheckerEnabled() {
        return updateCheckerEnabled;
    }

    public boolean updateCheckerShowChangeLog() {
        return updateCheckerShowChangeLog;
    }

    public boolean updateCheckerCheckOnStartup() {
        return updateCheckerCheckOnStartup;
    }

    public boolean updateCheckerPeriodicCheck() {
        return updateCheckerPeriodicCheck;
    }

    public int updateCheckerIntervalHours() {
        return updateCheckerIntervalHours;
    }

    public String updateCheckerNotifyPermission() {
        return updateCheckerNotifyPermission;
    }

    public String updateCheckerReleaseApiUrl() {
        return updateCheckerReleaseApiUrl;
    }

    public boolean allowShulkerBoxes() {
        return allowShulkerBoxes;
    }

    public boolean allowBundles() {
        return allowBundles;
    }

    public Sound backpackOpenSound() {
        return backpackOpenSound;
    }

    public Sound backpackCloseSound() {
        return backpackCloseSound;
    }

    public Set<Material> backpackInsertBlacklist() {
        return backpackInsertBlacklist;
    }

    public boolean isAllowedInBackpack(ItemStack stack) {
        if (ItemStacks.isAir(stack))
            return true;

        Material mat = stack.getType();

        // Admin-controlled hard blacklist
        if (backpackInsertBlacklist != null && backpackInsertBlacklist.contains(mat))
            return false;

        if (!allowShulkerBoxes && isShulkerBox(mat))
            return false;

        if (!allowBundles && isBundle(mat))
            return false;

        return true;
    }

    public boolean isPlaceableEnabled() {
        return placeableEnabled;
    }

    public boolean shouldDropPlacedBackpacksOnExplosion() {
        return dropPlacedBackpacksOnExplosion;
    }

    public double placedBackpackRenderOffsetX() {
        return placedBackpackRenderOffsetX;
    }

    public double placedBackpackRenderOffsetY() {
        return placedBackpackRenderOffsetY;
    }

    public double placedBackpackRenderOffsetZ() {
        return placedBackpackRenderOffsetZ;
    }

    public double placedBackpackRenderRotationX() {
        return placedBackpackRenderRotationX;
    }

    public double placedBackpackRenderRotationY() {
        return placedBackpackRenderRotationY;
    }

    public double placedBackpackRenderRotationZ() {
        return placedBackpackRenderRotationZ;
    }

    public double placedBackpackRenderScaleX() {
        return placedBackpackRenderScaleX;
    }

    public double placedBackpackRenderScaleY() {
        return placedBackpackRenderScaleY;
    }

    public double placedBackpackRenderScaleZ() {
        return placedBackpackRenderScaleZ;
    }

    private static Material mat(String name, Material fallback) {
        if (name == null)
            return fallback;
        Material m = parseMaterial(name);
        return m != null ? m : fallback;
    }

    private static int parseDefaultColor(ConfigurationSection s, int fallback) {
        if (s == null)
            return fallback;

        Object raw = s.get("DefaultColor");
        if (raw == null)
            return fallback;

        if (raw instanceof Number n) {
            return n.intValue() & 0xFFFFFF;
        }

        String text = String.valueOf(raw).trim();
        if (text.isEmpty())
            return fallback;

        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }

        try {
            if (text.matches("^[0-9A-Fa-f]{6}$")) {
                return Integer.parseInt(text, 16) & 0xFFFFFF;
            }
            return Integer.parseInt(text) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean isShulkerBox(Material mat) {
        return mat != null && mat.name().endsWith("SHULKER_BOX");
    }

    private static boolean isBundle(Material mat) {
        if (mat == null)
            return false;
        String name = mat.name();
        return name.equals("BUNDLE") || name.endsWith("_BUNDLE");
    }

    private static Material parseMaterial(String name) {
        if (name == null)
            return null;
        String s = name.trim();
        if (s.isEmpty())
            return null;
        if (s.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            s = s.substring("minecraft:".length());
        }
        s = s.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
        return Material.getMaterial(s);
    }

    private static Sound parseSound(String raw, Sound fallback) {
        if (raw == null)
            return fallback;
        String s = raw.trim();
        if (s.isEmpty())
            return fallback;

        String upper = s.toUpperCase(Locale.ROOT);

        // Back-compat shorthands
        if (upper.equals("CHEST_OPEN")) {
            s = "block.chest.open";
        } else if (upper.equals("CHEST_CLOSE")) {
            s = "block.chest.close";
        }

        NamespacedKey key = null;

        if (s.contains(":")) {
            key = NamespacedKey.fromString(s);
        } else if (s.indexOf('.') >= 0) {
            key = NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT));
        } else if (upper.matches("[A-Z0-9_]+")) {
            // Support "BLOCK_CHEST_OPEN" style -> "block.chest.open"
            key = NamespacedKey.minecraft(upper.toLowerCase(Locale.ROOT).replace('_', '.'));
        }

        if (key != null) {
            Sound resolved = Registry.SOUNDS.get(key);
            if (resolved != null)
                return resolved;
        }

        return fallback;
    }

    private static ConfigurationSection firstRecipeSection(ConfigurationSection typeSec) {
        if (typeSec == null)
            return null;

        ConfigurationSection sec = typeSec.getConfigurationSection("CraftingRecipe");
        if (sec != null) {
            if (sec.contains("Type") || sec.contains("Pattern") || sec.contains("Ingredients")
                    || sec.contains("OutputMaterial")
                    || sec.contains("DisplayName") || sec.contains("Lore") || sec.contains("CustomModelData")) {
                return sec;
            }
            for (String k : sec.getKeys(false)) {
                ConfigurationSection child = sec.getConfigurationSection(k);
                if (child != null)
                    return child;
            }
        }

        List<?> rawList = typeSec.getList("CraftingRecipe");
        if (rawList != null) {
            for (Object elem : rawList) {
                ConfigurationSection direct = asSection(elem);
                if (direct != null)
                    return direct;
                if (elem instanceof Map<?, ?> wrapper) {
                    if (wrapper.containsKey("Type") || wrapper.containsKey("Pattern")
                            || wrapper.containsKey("Ingredients")
                            || wrapper.containsKey("OutputMaterial") || wrapper.containsKey("DisplayName")
                            || wrapper.containsKey("Lore") || wrapper.containsKey("CustomModelData")) {
                        ConfigurationSection cs = asSection(wrapper);
                        if (cs != null)
                            return cs;
                    }
                    for (Object v : wrapper.values()) {
                        ConfigurationSection cs = asSection(v);
                        if (cs != null)
                            return cs;
                    }
                }
            }
        }

        return null;
    }

    private static ConfigurationSection asSection(Object value) {
        if (value == null)
            return null;
        if (value instanceof ConfigurationSection cs)
            return cs;
        if (value instanceof Map<?, ?> m) {
            MemoryConfiguration mem = new MemoryConfiguration();
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) m;
            return mem.createSection("r", typed);
        }
        return null;
    }

    private static String firstString(ConfigurationSection typeSec, String path, String fallback) {
        if (typeSec == null)
            return fallback;
        String v = typeSec.getString(path, null);
        if (v != null)
            return v;
        ConfigurationSection first = firstRecipeSection(typeSec);
        if (first != null) {
            String v2 = first.getString(path, null);
            if (v2 != null)
                return v2;
        }
        return fallback;
    }

    private static int firstInt(ConfigurationSection typeSec, String path, int fallback) {
        if (typeSec == null)
            return fallback;
        if (typeSec.contains(path))
            return typeSec.getInt(path, fallback);
        ConfigurationSection first = firstRecipeSection(typeSec);
        if (first != null && first.contains(path))
            return first.getInt(path, fallback);
        return fallback;
    }

    private static List<String> firstStringList(ConfigurationSection typeSec, String path) {
        if (typeSec == null)
            return List.of();
        List<String> v = typeSec.getStringList(path);
        if (v != null && !v.isEmpty())
            return v;
        ConfigurationSection first = firstRecipeSection(typeSec);
        if (first != null) {
            List<String> v2 = first.getStringList(path);
            if (v2 != null && !v2.isEmpty())
                return v2;
        }
        return List.of();
    }

    public BackpackTypeDef getType(String input) {
        return findType(input);
    }

    public BackpackTypeDef findType(String input) {
        if (input == null)
            return null;
        return types.get(input.toLowerCase(Locale.ROOT));
    }

    public Collection<BackpackTypeDef> getTypes() {
        return types.values();
    }

    public UpgradeDef getUpgrade(String input) {
        return findUpgrade(input);
    }

    public UpgradeDef findUpgrade(String input) {
        if (input == null)
            return null;
        return upgrades.get(input.toLowerCase(Locale.ROOT));
    }

    /**
     * Register a custom upgrade definition programmatically.
     * This allows external plugins to register module definitions for custom
     * modules.
     * 
     * @param def The upgrade definition to register
     * @throws IllegalArgumentException if def is null or ID already exists
     */
    public void registerCustomUpgrade(UpgradeDef def) {
        if (def == null) {
            throw new IllegalArgumentException("UpgradeDef cannot be null");
        }
        String key = def.id().toLowerCase(Locale.ROOT);
        if (upgrades.containsKey(key)) {
            throw new IllegalArgumentException("Upgrade with ID '" + def.id() + "' is already registered");
        }
        upgrades.put(key, def);
    }

    /**
     * Check if an upgrade is registered.
     * 
     * @param upgradeId The upgrade ID
     * @return true if the upgrade is registered
     */
    public boolean isUpgradeRegistered(String upgradeId) {
        if (upgradeId == null)
            return false;
        return upgrades.containsKey(upgradeId.toLowerCase(Locale.ROOT));
    }

    public Collection<UpgradeDef> getUpgrades() {
        return upgrades.values();
    }

    public Material navPageButtons() {
        return navPageButtons;
    }

    public Material navBorderFiller() {
        return navBorderFiller;
    }

    public Material unlockedUpgradeSlotMaterial() {
        return unlockedUpgradeSlotMaterial;
    }

    public Material lockedUpgradeSlotMaterial() {
        return lockedUpgradeSlotMaterial;
    }

    public boolean isSharedBackpacksEnabled() {
        return sharedBackpacksEnabled;
    }

    public int getMaxSharedUsers() {
        return maxSharedUsers;
    }

    private static String resolveUpdateCheckerRoot(FileConfiguration cfg) {
        if (cfg.isConfigurationSection("modularpacks.UpdateChecker")) {
            return "modularpacks.UpdateChecker";
        }
        return "modularpacks.UpdateChecker";
    }
}