package io.github.tootertutor.ModularPacks.api.modules;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import io.github.tootertutor.ModularPacks.config.ScreenType;

/**
 * Central registry for all module types.
 * Manages registration, lookup, and session tracking for modules.
 */
public final class ModuleRegistry {

    private final Map<String, IModule> modules = new ConcurrentHashMap<>();
    private final Map<ScreenType, IModule> modulesByScreenType = new ConcurrentHashMap<>();
    private final Logger logger;

    public ModuleRegistry(Logger logger) {
        this.logger = logger;
    }

    /**
     * Register a module type in the registry.
     * 
     * @param module The module to register
     * @throws IllegalArgumentException if a module with the same ID is already
     *                                  registered
     */
    public void registerModule(IModule module) {
        if (module == null) {
            throw new IllegalArgumentException("Module cannot be null");
        }

        String moduleId = module.getModuleId();
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("Module ID cannot be null or blank");
        }

        if (modules.containsKey(moduleId)) {
            throw new IllegalArgumentException("Module with ID '" + moduleId + "' is already registered");
        }

        modules.put(moduleId, module);

        // Also register by screen type for quick lookup
        ScreenType screenType = module.getScreenType();
        if (screenType != null && screenType != ScreenType.NONE) {
            modulesByScreenType.put(screenType, module);
        }

        logger.info("Registered module: " + moduleId + " (Screen: " + screenType + ")");
    }

    /**
     * Unregister a module from the registry.
     * 
     * @param moduleId The ID of the module to unregister
     * @return true if the module was unregistered, false if it wasn't registered
     */
    public boolean unregisterModule(String moduleId) {
        IModule removed = modules.remove(moduleId);
        if (removed != null) {
            modulesByScreenType.remove(removed.getScreenType());
            logger.info("Unregistered module: " + moduleId);
            return true;
        }
        return false;
    }

    /**
     * Get a module by its ID.
     * 
     * @param moduleId The module ID
     * @return Optional containing the module if found
     */
    public Optional<IModule> getModule(String moduleId) {
        return Optional.ofNullable(modules.get(moduleId));
    }

    /**
     * Get a module by its screen type.
     * 
     * @param screenType The screen type
     * @return Optional containing the module if found
     */
    public Optional<IModule> getModuleByScreenType(ScreenType screenType) {
        return Optional.ofNullable(modulesByScreenType.get(screenType));
    }

    /**
     * Get all registered modules.
     * 
     * @return Unmodifiable collection of all modules
     */
    public Collection<IModule> getAllModules() {
        return modules.values();
    }

    /**
     * Check if any module has an active session for the given player.
     * 
     * @param player The player to check
     * @return Optional containing the module with an active session, if any
     */
    public Optional<IModule> getActiveModule(Player player) {
        if (player == null) {
            return Optional.empty();
        }

        return modules.values().stream()
                .filter(module -> module.hasSession(player))
                .findFirst();
    }

    /**
     * Get the backpack ID for a player's active session across all modules.
     * 
     * @param player The player
     * @return The backpack UUID if the player has an active session, null otherwise
     */
    public UUID getActiveBackpackId(Player player) {
        return getActiveModule(player)
                .map(module -> module.getSessionBackpackId(player))
                .orElse(null);
    }

    /**
     * Check if a module is registered.
     * 
     * @param moduleId The module ID
     * @return true if registered
     */
    public boolean isRegistered(String moduleId) {
        return modules.containsKey(moduleId);
    }

    /**
     * Get the count of registered modules.
     * 
     * @return Number of registered modules
     */
    public int getModuleCount() {
        return modules.size();
    }

    /**
     * Clear all registered modules.
     * Use with caution - typically only for plugin disable/reload.
     */
    public void clearAll() {
        logger.info("Clearing all registered modules");
        modules.clear();
        modulesByScreenType.clear();
    }
}
