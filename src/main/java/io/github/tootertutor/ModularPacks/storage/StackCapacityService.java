package io.github.tootertutor.ModularPacks.storage;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.UpgradeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

/**
 * Dynamically derives logical slot capacity from installed Stack Upgrades.
 */
public final class StackCapacityService implements StackCapacityProvider {

    private final Function<String, UpgradeDef> definitionResolver;
    private final BiFunction<BackpackData, UUID, String> moduleTypeResolver;

    public StackCapacityService(ModularPacksPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        this.definitionResolver = plugin.cfg()::findUpgrade;
        this.moduleTypeResolver = (data, moduleId) -> readModuleType(plugin, data, moduleId);
    }

    public StackCapacityService(Function<String, UpgradeDef> definitionResolver,
            BiFunction<BackpackData, UUID, String> moduleTypeResolver) {
        if (definitionResolver == null || moduleTypeResolver == null) {
            throw new IllegalArgumentException("capacity resolvers cannot be null");
        }
        this.definitionResolver = definitionResolver;
        this.moduleTypeResolver = moduleTypeResolver;
    }

    public long capacityFor(BackpackData data, ItemStack prototype) {
        return capacityFor(data, prototype, null);
    }

    @Override
    public long capacityFor(BackpackData data, ItemStack prototype, UUID excludedModuleId) {
        if (prototype == null || prototype.getType().isAir()) {
            throw new IllegalArgumentException("prototype must be non-air");
        }

        long capacity = prototype.getMaxStackSize();
        if (data == null) {
            return capacity;
        }

        for (UUID moduleId : data.installedModules().values()) {
            if (moduleId == null || moduleId.equals(excludedModuleId)) {
                continue;
            }
            UpgradeDef definition = definitionFor(data, moduleId);
            if (definition == null || definition.stackMultiplier() <= 1) {
                continue;
            }
            capacity = multiplySaturated(capacity, definition.stackMultiplier());
        }
        return capacity;
    }

    public long multiplierFor(BackpackData data) {
        return multiplierFor(data, null);
    }

    public long multiplierFor(BackpackData data, UUID excludedModuleId) {
        long multiplier = 1;
        if (data == null) {
            return multiplier;
        }
        for (UUID moduleId : data.installedModules().values()) {
            if (moduleId == null || moduleId.equals(excludedModuleId)) {
                continue;
            }
            UpgradeDef definition = definitionFor(data, moduleId);
            if (definition != null && definition.stackMultiplier() > 1) {
                multiplier = multiplySaturated(multiplier, definition.stackMultiplier());
            }
        }
        return multiplier;
    }

    public CapacityViolation firstViolation(BackpackData data, BackpackStorage storage, UUID excludedModuleId) {
        if (storage == null) {
            throw new IllegalArgumentException("storage cannot be null");
        }
        for (int slot = 0; slot < storage.size(); slot++) {
            StoredStack stored = storage.get(slot);
            if (stored == null) {
                continue;
            }
            long capacity = capacityFor(data, stored.prototype(), excludedModuleId);
            if (stored.count() > capacity) {
                return new CapacityViolation(slot, stored.count(), capacity);
            }
        }
        return null;
    }

    public record CapacityViolation(int slot, long storedCount, long capacity) {
    }

    public static long multiplySaturated(long value, long multiplier) {
        if (value < 0 || multiplier < 0) {
            throw new IllegalArgumentException("capacity factors must be non-negative");
        }
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private UpgradeDef definitionFor(BackpackData data, UUID moduleId) {
        String moduleType = moduleTypeResolver.apply(data, moduleId);
        return moduleType == null ? null : definitionResolver.apply(moduleType);
    }

    private static String readModuleType(ModularPacksPlugin plugin, BackpackData data, UUID moduleId) {
        byte[] snapshot = data.installedSnapshots().get(moduleId);
        if (snapshot == null) {
            return null;
        }
        try {
            ItemStack[] items = ItemStackCodec.fromBytes(snapshot);
            if (items.length == 0 || items[0] == null) {
                return null;
            }
            ItemMeta meta = items[0].getItemMeta();
            if (meta == null) {
                return null;
            }
            return meta.getPersistentDataContainer()
                    .get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
