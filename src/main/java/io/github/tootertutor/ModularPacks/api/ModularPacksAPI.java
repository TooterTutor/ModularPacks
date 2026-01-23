package io.github.tootertutor.ModularPacks.api;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.modules.IModule;
import io.github.tootertutor.ModularPacks.api.modules.ModuleRegistry;
import io.github.tootertutor.ModularPacks.config.ScreenType;

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
}
