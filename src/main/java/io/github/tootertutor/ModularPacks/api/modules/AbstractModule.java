package io.github.tootertutor.ModularPacks.api.modules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

/**
 * Abstract base class providing common module functionality.
 * Handles session management, state persistence, and common patterns.
 */
public abstract class AbstractModule implements IModule {

    /**
     * Session record tracking a player's active module session.
     */
    public record ModuleSession(UUID backpackId, String backpackType, UUID moduleId) {
    }

    protected final Map<UUID, ModuleSession> sessions = new ConcurrentHashMap<>();
    private final String moduleId;
    private final ScreenType screenType;
    private final String displayName;

    /**
     * Create an abstract module with the given properties.
     * 
     * @param moduleId    Unique identifier for this module type
     * @param screenType  The vanilla inventory screen type this module uses
     * @param displayName User-friendly name for this module
     */
    protected AbstractModule(String moduleId, ScreenType screenType, String displayName) {
        this.moduleId = moduleId;
        this.screenType = screenType;
        this.displayName = displayName;
    }

    @Override
    public String getModuleId() {
        return moduleId;
    }

    @Override
    public ScreenType getScreenType() {
        return screenType;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean hasSession(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    @Override
    public UUID getSessionBackpackId(Player player) {
        if (player == null)
            return null;
        ModuleSession session = sessions.get(player.getUniqueId());
        return session == null ? null : session.backpackId();
    }

    /**
     * Get the module ID from the active session for a player.
     * 
     * @param player The player
     * @return The module ID, or null if no session exists
     */
    public UUID getSessionModuleId(Player player) {
        if (player == null)
            return null;
        ModuleSession session = sessions.get(player.getUniqueId());
        return session == null ? null : session.moduleId();
    }

    /**
     * Get the backpack type from the active session for a player.
     * 
     * @param player The player
     * @return The backpack type, or null if no session exists
     */
    public String getSessionBackpackType(Player player) {
        if (player == null)
            return null;
        ModuleSession session = sessions.get(player.getUniqueId());
        return session == null ? null : session.backpackType();
    }

    /**
     * Get the active session for a player.
     * 
     * @param player The player
     * @return The session, or null if none exists
     */
    public ModuleSession getSession(Player player) {
        if (player == null)
            return null;
        return sessions.get(player.getUniqueId());
    }

    /**
     * Register a new session for a player.
     * 
     * @param player       The player
     * @param backpackId   The backpack UUID
     * @param backpackType The backpack type
     * @param moduleId     The module instance ID
     */
    protected void createSession(Player player, UUID backpackId, String backpackType, UUID moduleId) {
        if (player != null) {
            sessions.put(player.getUniqueId(), new ModuleSession(backpackId, backpackType, moduleId));
        }
    }

    /**
     * Remove and return a player's session.
     * 
     * @param player The player
     * @return The removed session, or null if none existed
     */
    protected ModuleSession removeSession(Player player) {
        if (player == null)
            return null;
        return sessions.remove(player.getUniqueId());
    }

    /**
     * Load persisted module state from the backpack data.
     * 
     * @param plugin       The plugin instance
     * @param backpackId   The backpack UUID
     * @param backpackType The backpack type
     * @param moduleId     The module instance ID
     * @return The state byte array, or null if none exists
     */
    protected byte[] loadState(ModularPacksPlugin plugin, UUID backpackId, String backpackType, UUID moduleId) {
        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
        return data.moduleStates().get(moduleId);
    }

    /**
     * Save module state to the backpack data.
     * 
     * @param plugin       The plugin instance
     * @param backpackId   The backpack UUID
     * @param backpackType The backpack type
     * @param moduleId     The module instance ID
     * @param stateBytes   The state to save
     */
    protected void saveState(ModularPacksPlugin plugin, UUID backpackId, String backpackType, UUID moduleId,
            byte[] stateBytes) {
        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
        data.moduleStates().put(moduleId, stateBytes);
        plugin.repo().saveBackpack(data);
        plugin.sessions().refreshLinkedBackpacksThrottled(backpackId, data);
    }

    /**
     * Helper to load an ItemStack array from state bytes.
     * 
     * @param stateBytes The state bytes
     * @return The ItemStack array, or empty array if decode fails
     */
    protected ItemStack[] loadItemStackArray(byte[] stateBytes) {
        if (stateBytes == null || stateBytes.length == 0)
            return new ItemStack[0];
        try {
            return ItemStackCodec.fromBytes(stateBytes);
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    /**
     * Helper to encode an ItemStack array to state bytes.
     * 
     * @param items The items to encode
     * @return The encoded byte array
     */
    protected byte[] saveItemStackArray(ItemStack[] items) {
        return ItemStackCodec.toBytes(items);
    }

    /**
     * Notify the session manager that the player closed a module.
     * 
     * @param plugin     The plugin instance
     * @param player     The player
     * @param backpackId The backpack ID
     */
    protected void notifySessionClose(ModularPacksPlugin plugin, Player player, UUID backpackId) {
        plugin.sessions().onRelatedInventoryClose(player, backpackId);
    }

    @Override
    public void handleClose(ModularPacksPlugin plugin, Player player, Inventory inventory) {
        if (plugin == null || player == null || inventory == null)
            return;

        ModuleSession session = removeSession(player);
        if (session == null)
            return;

        // Save state
        byte[] stateBytes = serializeState(inventory);
        saveState(plugin, session.backpackId(), session.backpackType(), session.moduleId(), stateBytes);

        // Clear inventory to prevent vanilla from returning items
        clearInventory(inventory);

        // Notify session manager
        notifySessionClose(plugin, player, session.backpackId());
    }

    /**
     * Serialize the inventory state to bytes.
     * Subclasses should override to implement custom serialization.
     * 
     * @param inventory The inventory to serialize
     * @return The serialized state
     */
    protected abstract byte[] serializeState(Inventory inventory);

    /**
     * Deserialize state bytes and populate the inventory.
     * Subclasses should override to implement custom deserialization.
     * 
     * @param inventory  The inventory to populate
     * @param stateBytes The state to deserialize
     */
    protected abstract void deserializeState(Inventory inventory, byte[] stateBytes);

    /**
     * Clear the inventory before it's closed.
     * Override to customize which slots should be cleared.
     * 
     * @param inventory The inventory to clear
     */
    protected void clearInventory(Inventory inventory) {
        inventory.clear();
    }
}
