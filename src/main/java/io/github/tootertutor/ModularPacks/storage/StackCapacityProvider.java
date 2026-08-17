package io.github.tootertutor.ModularPacks.storage;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.data.BackpackData;

@FunctionalInterface
public interface StackCapacityProvider {

    long capacityFor(BackpackData data, ItemStack prototype, UUID excludedModuleId);
}
