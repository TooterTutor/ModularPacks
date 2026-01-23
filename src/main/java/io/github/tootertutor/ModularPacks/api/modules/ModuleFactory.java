package io.github.tootertutor.ModularPacks.api.modules;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;

/**
 * Factory for creating and registering module instances.
 * Supports both built-in modules and custom module registration from external
 * plugins.
 */
public class ModuleFactory {

    private final ModularPacksPlugin plugin;
    private final ModuleRegistry registry;
    private final Map<String, Function<ModularPacksPlugin, IModule>> moduleConstructors;

    /**
     * Create a new module factory.
     * 
     * @param plugin   The plugin instance
     * @param registry The module registry
     */
    public ModuleFactory(ModularPacksPlugin plugin, ModuleRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.moduleConstructors = new HashMap<>();
    }

    /**
     * Register a module constructor for a built-in module type.
     * This allows modules to be instantiated on demand.
     * 
     * @param moduleId    The module ID
     * @param constructor Function that creates the module instance
     * @return This factory for chaining
     */
    public ModuleFactory registerConstructor(String moduleId, Function<ModularPacksPlugin, IModule> constructor) {
        moduleConstructors.put(moduleId, constructor);
        return this;
    }

    /**
     * Create and register a module using its registered constructor.
     * 
     * @param moduleId The module ID
     * @return The created module, or null if no constructor is registered
     */
    public IModule createAndRegister(String moduleId) {
        Function<ModularPacksPlugin, IModule> constructor = moduleConstructors.get(moduleId);
        if (constructor == null) {
            return null;
        }

        IModule module = constructor.apply(plugin);
        if (module != null) {
            registry.registerModule(module);
        }
        return module;
    }

    /**
     * Create and register all built-in modules.
     * This is called during plugin initialization.
     * 
     * @return The number of modules registered
     */
    public int registerAllBuiltInModules() {
        int count = 0;
        for (String moduleId : moduleConstructors.keySet()) {
            if (createAndRegister(moduleId) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Register a custom module instance directly.
     * This is used by external plugins to add their own modules.
     * 
     * @param module The module to register
     * @throws IllegalArgumentException if module is null or already registered
     */
    public void registerCustomModule(IModule module) {
        if (module == null) {
            throw new IllegalArgumentException("Module cannot be null");
        }
        registry.registerModule(module);
    }

    /**
     * Check if a module constructor is registered.
     * 
     * @param moduleId The module ID
     * @return true if a constructor exists
     */
    public boolean hasConstructor(String moduleId) {
        return moduleConstructors.containsKey(moduleId);
    }

    /**
     * Get the module registry.
     * 
     * @return The registry
     */
    public ModuleRegistry getRegistry() {
        return registry;
    }

    /**
     * Get the plugin instance.
     * 
     * @return The plugin
     */
    public ModularPacksPlugin getPlugin() {
        return plugin;
    }

    /**
     * Clear all registered constructors.
     * This is called during plugin shutdown.
     */
    public void clearConstructors() {
        moduleConstructors.clear();
    }
}
