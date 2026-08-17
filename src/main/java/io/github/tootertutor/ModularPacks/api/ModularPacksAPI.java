package io.github.tootertutor.ModularPacks.api;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.modules.IModule;
import io.github.tootertutor.ModularPacks.api.modules.ModuleRegistry;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.config.UpgradeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.storage.StoredStack;

/**
 * Public API for ModularPacks.
 * External plugins can use this class to interact with ModularPacks,
 * register custom modules, and hook into module functionality.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * ModularPacksAPI api = ModularPacksAPI.getInstance();
 * if (api != null) {
 *     api.registerModule(new MyCustomModule());
 * }
 * }</pre>
 */
public class ModularPacksAPI {

    private static ModularPacksAPI instance;

    private final ModularPacksPlugin plugin;
    private final ModuleRegistry moduleRegistry;

    private ModularPacksAPI(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.moduleRegistry = new ModuleRegistry(plugin.getLogger());
    }

    /**
     * Initialize the API. Called by ModularPacks during plugin startup.
     * External plugins should NOT call this method.
     * 
     * @param plugin The ModularPacks plugin instance
     */
    public static void initialize(ModularPacksPlugin plugin) {
        if (instance == null) {
            instance = new ModularPacksAPI(plugin);
        }
    }

    /**
     * Get the API instance.
     * 
     * @return The API instance, or null if ModularPacks is not loaded
     */
    public static ModularPacksAPI getInstance() {
        return instance;
    }

    /**
     * Get the API instance safely with dependency check.
     * 
     * @return The API instance, or null if ModularPacks is not loaded
     */
    public static ModularPacksAPI getAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ModularPacks");
        if (plugin instanceof ModularPacksPlugin) {
            return getInstance();
        }
        return null;
    }

    /**
     * Get the ModularPacks plugin instance.
     * 
     * @return The plugin instance
     */
    public ModularPacksPlugin getPlugin() {
        return plugin;
    }

    /**
     * Get the module registry.
     * 
     * @return The module registry
     */
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    /**
     * Register a custom module.
     * Note: This only registers the module implementation, not the item definition.
     * To allow giving and crafting the module, use registerModule(IModule,
     * UpgradeDef) instead.
     * 
     * @param module The module to register
     * @throws IllegalArgumentException if module is null or already registered
     */
    public void registerModule(IModule module) {
        if (module == null) {
            throw new IllegalArgumentException("Module cannot be null");
        }
        moduleRegistry.registerModule(module);
    }

    /**
     * Register a custom module with its item definition.
     * This allows the module to be given via commands and crafted in recipes.
     * 
     * <p>
     * Example usage:
     * 
     * <pre>{@code
     * ModularPacksAPI api = ModularPacksAPI.getInstance();
     * if (api != null) {
     *     MyCustomModule module = new MyCustomModule();
     *     UpgradeDef def = new UpgradeDef(
     *             "MyModule", // id
     *             "&6My Custom Module", // displayName
     *             Material.DIAMOND, // material
     *             List.of("&7Custom functionality"), // lore
     *             1001, // customModelData
     *             false, // glint
     *             true, // enabled
     *             true, // toggleable
     *             false, // secondaryAction
     *             ScreenType.GENERIC // screenType
     *     );
     *     api.registerModule(module, def);
     * }
     * }</pre>
     * 
     * @param module     The module to register
     * @param upgradeDef The upgrade definition for creating items
     * @throws IllegalArgumentException if module or upgradeDef is null, or if
     *                                  already registered
     */
    public void registerModule(IModule module, UpgradeDef upgradeDef) {
        if (module == null) {
            throw new IllegalArgumentException("Module cannot be null");
        }
        if (upgradeDef == null) {
            throw new IllegalArgumentException("UpgradeDef cannot be null");
        }

        // Validate that IDs match
        if (!module.getModuleId().equalsIgnoreCase(upgradeDef.id())) {
            throw new IllegalArgumentException(
                    "Module ID '" + module.getModuleId() + "' does not match UpgradeDef ID '" + upgradeDef.id() + "'");
        }

        // Register both the module implementation and the item definition
        moduleRegistry.registerModule(module);
        plugin.cfg().registerCustomUpgrade(upgradeDef);
    }

    /**
     * Unregister a module by ID.
     * 
     * @param moduleId The module ID
     * @return true if the module was removed, false if it didn't exist
     */
    public boolean unregisterModule(String moduleId) {
        return moduleRegistry.unregisterModule(moduleId);
    }

    /**
     * Get a module by its ID.
     * 
     * @param moduleId The module ID
     * @return The module, or null if not found
     */
    public IModule getModule(String moduleId) {
        return moduleRegistry.getModule(moduleId).orElse(null);
    }

    /**
     * Get a module by screen type.
     * This returns the first registered module that uses the given screen type.
     * 
     * @param screenType The screen type
     * @return The module, or null if no module is registered for this screen type
     */
    public IModule getModuleByScreenType(ScreenType screenType) {
        return moduleRegistry.getModuleByScreenType(screenType).orElse(null);
    }

    /**
     * Get all registered modules.
     * 
     * @return Collection of all registered modules
     */
    public Collection<IModule> getAllModules() {
        return moduleRegistry.getAllModules();
    }

    /**
     * Get the active module for a player.
     * 
     * @param player The player
     * @return The active module, or null if player has no active module session
     */
    public IModule getActiveModule(Player player) {
        return moduleRegistry.getActiveModule(player).orElse(null);
    }

    /**
     * Get the backpack ID for a player's active module session.
     * 
     * @param player The player
     * @return The backpack UUID, or null if player has no active session
     */
    public UUID getActiveBackpackId(Player player) {
        return moduleRegistry.getActiveBackpackId(player);
    }

    /**
     * Check if a module is registered.
     * 
     * @param moduleId The module ID
     * @return true if the module is registered
     */
    public boolean isModuleRegistered(String moduleId) {
        return moduleRegistry.isRegistered(moduleId);
    }

    /**
     * Get the total number of registered modules.
     * 
     * @return The module count
     */
    public int getModuleCount() {
        return moduleRegistry.getModuleCount();
    }

    /**
     * Get the full logical storage contents for a backpack UUID.
     *
     * <p>
     * The returned array length is always {@code rows * 9} for the backpack type.
     * Changes to the returned array are not persisted until one of the write
     * methods
     * in this API is called.
     * This compatibility view can represent only vanilla-capacity stacks; plugins
     * should migrate to {@link #getLogicalBackpackStorage(UUID)} before virtual
     * stack capacities are enabled.
     *
     * @param backpackId Backpack UUID
     * @return cloned logical contents array
     * @throws IllegalArgumentException if backpackId is null or no backpack row
     *                                  exists
     */
    @Deprecated(forRemoval = false)
    public ItemStack[] getBackpackStorage(UUID backpackId) {
        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        return ctx.storage.toVanillaContents();
    }

    /**
     * Returns a detached logical-storage snapshot. Mutating it does not persist
     * changes.
     */
    public BackpackStorage getLogicalBackpackStorage(UUID backpackId) {
        return loadBackpackStorageContext(backpackId).storage.copy();
    }

    /**
     * Get the item in a logical slot.
     *
     * @param backpackId Backpack UUID
     * @param slot       Logical slot index (0-based)
     * @return cloned item in slot, or null if empty
     */
    public ItemStack getBackpackItem(UUID backpackId, int slot) {
        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = validateLogicalSlot(slot, ctx.storage.size());
        StoredStack stored = ctx.storage.get(idx);
        return materializeCompatibilityStack(stored);
    }

    /**
     * Get the item in a page-local slot.
     *
     * @param backpackId Backpack UUID
     * @param page       Page index (0-based)
     * @param slot       Slot within page (0-based, max 44)
     * @return cloned item in slot, or null if empty
     */
    public ItemStack getBackpackItem(UUID backpackId, int page, int slot) {
        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = pageSlotToLogicalIndex(page, slot, ctx.storage.size());
        StoredStack stored = ctx.storage.get(idx);
        return materializeCompatibilityStack(stored);
    }

    /**
     * Insert into any available slot in the backpack (merge first, then empty
     * slots).
     *
     * @param backpackId Backpack UUID
     * @param stack      Item stack to insert
     * @return null if fully inserted, otherwise the remainder
     */
    public ItemStack insertBackpackItem(UUID backpackId, ItemStack stack) {
        if (isAir(stack) || !plugin.cfg().isAllowedInBackpack(stack) || isBackpack(stack)) {
            return cloneOrNull(stack);
        }

        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        long inserted = plugin.backpackStorage().insert(ctx.data, ctx.storage, stack);
        if (inserted > 0) {
            persistBackpackStorage(ctx);
        }
        long remaining = stack.getAmount() - inserted;
        return remaining == 0 ? null
                : plugin.backpackStorage().materialize(stack, Math.toIntExact(remaining));
    }

    /**
     * Insert into a specific logical slot.
     *
     * <p>
     * If the target slot is occupied by a similar stack, this method merges into
     * that
     * stack. Otherwise, insertion only succeeds when the target slot is empty.
     *
     * @param backpackId Backpack UUID
     * @param slot       Logical slot index (0-based)
     * @param stack      Item stack to insert
     * @return null if fully inserted, otherwise the remainder
     */
    public ItemStack insertBackpackItem(UUID backpackId, int slot, ItemStack stack) {
        if (isAir(stack) || !plugin.cfg().isAllowedInBackpack(stack) || isBackpack(stack)) {
            return cloneOrNull(stack);
        }

        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = validateLogicalSlot(slot, ctx.storage.size());

        long inserted = plugin.backpackStorage().insertIntoSlot(ctx.data, ctx.storage, idx, stack, stack.getAmount());
        if (inserted > 0) {
            persistBackpackStorage(ctx);
        }
        long remaining = stack.getAmount() - inserted;
        return remaining == 0 ? null
                : plugin.backpackStorage().materialize(stack, Math.toIntExact(remaining));
    }

    /**
     * Insert into a specific page-local slot.
     *
     * @param backpackId Backpack UUID
     * @param page       Page index (0-based)
     * @param slot       Slot within page (0-based, max 44)
     * @param stack      Item stack to insert
     * @return null if fully inserted, otherwise the remainder
     */
    public ItemStack insertBackpackItem(UUID backpackId, int page, int slot, ItemStack stack) {
        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = pageSlotToLogicalIndex(page, slot, ctx.storage.size());
        return insertBackpackItem(backpackId, idx, stack);
    }

    /**
     * Extract (remove) the entire stack from a logical slot.
     *
     * @param backpackId Backpack UUID
     * @param slot       Logical slot index (0-based)
     * @return extracted stack, or null when the slot is empty
     */
    public ItemStack extractBackpackItem(UUID backpackId, int slot) {
        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = validateLogicalSlot(slot, ctx.storage.size());

        StoredStack existing = ctx.storage.get(idx);
        if (existing == null) {
            return null;
        }

        ensureVanillaRepresentable(existing);

        ItemStack extracted = plugin.backpackStorage().extractFromSlot(
                ctx.storage, idx, Math.toIntExact(existing.count()));
        persistBackpackStorage(ctx);
        return extracted;
    }

    /**
     * Extract (remove) the entire stack from a page-local slot.
     *
     * @param backpackId Backpack UUID
     * @param page       Page index (0-based)
     * @param slot       Slot within page (0-based, max 44)
     * @return extracted stack, or null when the slot is empty
     */
    public ItemStack extractBackpackItem(UUID backpackId, int page, int slot) {
        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = pageSlotToLogicalIndex(page, slot, ctx.storage.size());
        return extractBackpackItem(backpackId, idx);
    }

    /**
     * Extract up to {@code amount} items from a logical slot.
     *
     * @param backpackId Backpack UUID
     * @param slot       Logical slot index (0-based)
     * @param amount     Amount to extract; must be {@code > 0}
     * @return extracted stack, or null when the slot is empty
     */
    public ItemStack extractBackpackItemAmount(UUID backpackId, int slot, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }

        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = validateLogicalSlot(slot, ctx.storage.size());
        ItemStack extracted = plugin.backpackStorage().extractFromSlot(ctx.storage, idx, amount);
        if (extracted == null) {
            return null;
        }

        persistBackpackStorage(ctx);
        return extracted;
    }

    /**
     * Extract up to {@code amount} items from a page-local slot.
     *
     * @param backpackId Backpack UUID
     * @param page       Page index (0-based)
     * @param slot       Slot within page (0-based, max 44)
     * @param amount     Amount to extract; must be {@code > 0}
     * @return extracted stack, or null when the slot is empty
     */
    public ItemStack extractBackpackItemAmount(UUID backpackId, int page, int slot, int amount) {
        BackpackStorageContext ctx = loadBackpackStorageContext(backpackId);
        int idx = pageSlotToLogicalIndex(page, slot, ctx.storage.size());
        return extractBackpackItemAmount(backpackId, idx, amount);
    }

    private BackpackStorageContext loadBackpackStorageContext(UUID backpackId) {
        if (backpackId == null) {
            throw new IllegalArgumentException("backpackId cannot be null");
        }

        String typeId = plugin.repo().findBackpackType(backpackId);
        if (typeId == null) {
            throw new IllegalArgumentException("Backpack not found for UUID: " + backpackId);
        }

        BackpackData data = plugin.repo().loadOrCreate(backpackId, typeId);
        BackpackTypeDef type = plugin.cfg().findType(data.backpackType());
        if (type == null) {
            throw new IllegalStateException(
                    "Unknown backpack type for UUID " + backpackId + ": " + data.backpackType());
        }

        int logicalSlots = type.rows() * 9;
        BackpackStorage storage = plugin.backpackStorage().load(data, logicalSlots);

        return new BackpackStorageContext(data, type, storage);
    }

    private void persistBackpackStorage(BackpackStorageContext ctx) {
        plugin.backpackStorage().save(ctx.data, ctx.storage);
        plugin.repo().saveBackpack(ctx.data);
        plugin.sessions().refreshLinkedBackpacksThrottled(ctx.data.backpackId(), ctx.data);
    }

    private static int validateLogicalSlot(int slot, int logicalSize) {
        if (slot < 0 || slot >= logicalSize) {
            throw new IllegalArgumentException("slot out of range: " + slot + " (size=" + logicalSize + ")");
        }
        return slot;
    }

    private static int pageSlotToLogicalIndex(int page, int slot, int logicalSize) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (slot < 0 || slot >= 45) {
            throw new IllegalArgumentException("page slot must be between 0 and 44");
        }

        int logicalIndex = page * 45 + slot;
        if (logicalIndex < 0 || logicalIndex >= logicalSize) {
            throw new IllegalArgumentException(
                    "page/slot out of range: page=" + page + ", slot=" + slot + ", size=" + logicalSize);
        }
        return logicalIndex;
    }

    private boolean isBackpack(ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String id = meta.getPersistentDataContainer().get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING);
        return id != null && !id.isBlank();
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private ItemStack materializeCompatibilityStack(StoredStack stored) {
        if (stored == null) {
            return null;
        }
        ensureVanillaRepresentable(stored);
        return plugin.backpackStorage().materialize(stored, Math.toIntExact(stored.count()));
    }

    private static void ensureVanillaRepresentable(StoredStack stored) {
        if (stored.count() > stored.prototype().getMaxStackSize()) {
            throw new IllegalStateException(
                    "Logical stack count " + stored.count()
                            + " cannot be represented by this legacy ItemStack API; use getLogicalBackpackStorage");
        }
    }

    private record BackpackStorageContext(BackpackData data, BackpackTypeDef type, BackpackStorage storage) {
    }
}
