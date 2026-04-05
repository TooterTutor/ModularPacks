package io.github.tootertutor.ModularPacks.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

/**
 * Generates Custom Model Data strings based on installed modules in a backpack.
 * 
 * This allows resource packs to detect which modules are installed and render
 * additional models on top of the base backpack model.
 * 
 * Format: `modularpacks.module.<moduleType>:true`
 * Example: If an "Everlasting" module is installed, the string would be:
 * `modularpacks.module.everlasting:true`
 */
public final class ModuleModelDataGenerator {

    private ModuleModelDataGenerator() {
    }

    /**
     * Generate Custom Model Data strings for all installed modules in a backpack.
     * 
     * @param plugin       The plugin instance
     * @param backpackData The backpack data containing module information
     * @return List of module-based CMD strings (e.g.,
     *         "modularpacks.module.everlasting:true")
     */
    public static List<String> generateModuleModelDataStrings(ModularPacksPlugin plugin, BackpackData backpackData) {
        Set<String> moduleStrings = new LinkedHashSet<>();

        if (backpackData == null) {
            return List.of();
        }

        Keys keys = plugin.keys();
        Map<Integer, UUID> installedModules = backpackData.installedModules();

        // Iterate through all installed modules
        for (UUID moduleId : installedModules.values()) {
            if (moduleId == null) {
                continue;
            }

            // Resolve the module snapshot to get its type
            ItemStack moduleSnapshot = resolveModuleSnapshot(backpackData, moduleId);
            if (moduleSnapshot == null || !moduleSnapshot.hasItemMeta()) {
                continue;
            }

            ItemMeta moduleMeta = moduleSnapshot.getItemMeta();
            if (moduleMeta == null) {
                continue;
            }

            // Get the module type from the PDC
            String moduleType = moduleMeta.getPersistentDataContainer().get(keys.MODULE_TYPE,
                    PersistentDataType.STRING);
            if (moduleType == null || moduleType.isBlank()) {
                continue;
            }

            // Check if the module is enabled (skip disabled modules)
            Byte enabledByte = moduleMeta.getPersistentDataContainer().get(keys.MODULE_ENABLED,
                    PersistentDataType.BYTE);
            if (enabledByte != null && enabledByte == 0) {
                // Module is disabled, skip it
                continue;
            }

            // Generate the CMD string: modularpacks.module.<type>:true
            String cmdString = "modularpacks.module." + moduleType.toLowerCase(Locale.ROOT) + ":true";
            moduleStrings.add(cmdString);
        }

        return new ArrayList<>(moduleStrings);
    }

    /**
     * Resolve a module's ItemStack snapshot from the backpack data.
     * 
     * @param backpackData The backpack data
     * @param moduleId     The module UUID
     * @return The module ItemStack snapshot, or null if not found
     */
    private static ItemStack resolveModuleSnapshot(BackpackData backpackData, UUID moduleId) {
        if (backpackData == null || moduleId == null) {
            return null;
        }

        byte[] snapshotBytes = backpackData.installedSnapshots().get(moduleId);
        if (snapshotBytes == null || snapshotBytes.length == 0) {
            return null;
        }

        try {
            ItemStack[] items = ItemStackCodec.fromBytes(snapshotBytes);
            if (items.length == 0) {
                return null;
            }
            return items[0];
        } catch (Exception e) {
            return null;
        }
    }
}
