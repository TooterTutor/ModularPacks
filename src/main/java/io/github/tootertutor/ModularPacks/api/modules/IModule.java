package io.github.tootertutor.ModularPacks.api.modules;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.ScreenType;

/**
 * Core interface that all modules must implement.
 * This allows for dynamic registration and handling of custom modules.
 */
public interface IModule {

    /**
     * Get the unique identifier for this module type.
     * This is used to register and look up modules.
     * 
     * @return The module's type identifier (e.g., "CRAFTING", "SMITHING",
     *         "CUSTOM_GRINDER")
     */
    String getModuleId();

    /**
     * Get the screen type associated with this module.
     * This determines which vanilla inventory type is used.
     * 
     * @return The ScreenType for this module
     */
    ScreenType getScreenType();

    /**
     * Check if this module has an active session for the given player.
     * 
     * @param player The player to check
     * @return true if the player has an active session with this module
     */
    boolean hasSession(Player player);

    /**
     * Get the backpack ID for the player's active session with this module.
     * 
     * @param player The player
     * @return The backpack UUID, or null if no session exists
     */
    UUID getSessionBackpackId(Player player);

    /**
     * Open this module's UI for the player.
     * 
     * @param plugin       The plugin instance
     * @param player       The player opening the module
     * @param backpackId   The backpack UUID this module belongs to
     * @param backpackType The type of backpack
     * @param moduleId     The unique ID of this module instance
     */
    void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId);

    /**
     * Handle closing the module UI and persist any necessary state.
     * 
     * @param plugin    The plugin instance
     * @param player    The player closing the module
     * @param inventory The inventory being closed
     */
    void handleClose(ModularPacksPlugin plugin, Player player, Inventory inventory);

    /**
     * Called when the module UI needs to update its result/output slot.
     * This is used by crafting-type modules to recompute outputs.
     * 
     * @param inventory The module's inventory
     */
    default void updateResult(Inventory inventory) {
        // Default: no-op for modules that don't have dynamic outputs
    }

    /**
     * Validate that an inventory view belongs to this module.
     * Used to ensure we're handling the correct inventory type.
     * 
     * @param view The inventory view to validate
     * @return true if this view is valid for this module
     */
    boolean isValidInventoryView(InventoryView view);

    /**
     * Get a user-friendly display name for this module type.
     * Used in UIs and messages.
     * 
     * @return The display name (e.g., "Crafting Module", "Custom Grinder")
     */
    String getDisplayName();

    /**
     * Check if this module type is enabled.
     * Allows for dynamic enabling/disabling of modules via config.
     * 
     * @return true if this module is enabled
     */
    default boolean isEnabled() {
        return true;
    }
}
