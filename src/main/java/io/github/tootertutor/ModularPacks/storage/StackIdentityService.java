package io.github.tootertutor.ModularPacks.storage;

import org.bukkit.inventory.ItemStack;

/**
 * Central item-identity comparison for logical stacks.
 *
 * Bukkit ItemStack equality is used after normalizing amounts, preserving all
 * material, metadata, component, persistent-data, and nested container details
 * represented by Bukkit.
 */
public final class StackIdentityService {

    public boolean sameIdentity(ItemStack first, ItemStack second) {
        if (isAir(first) || isAir(second)) {
            return false;
        }

        ItemStack normalizedFirst = first.clone();
        ItemStack normalizedSecond = second.clone();
        normalizedFirst.setAmount(1);
        normalizedSecond.setAmount(1);
        return normalizedFirst.equals(normalizedSecond);
    }

    public boolean sameIdentity(StoredStack stored, ItemStack item) {
        return stored != null && sameIdentity(stored.prototype(), item);
    }

    public boolean sameIdentity(StoredStack first, StoredStack second) {
        return first != null && second != null && sameIdentity(first.prototype(), second.prototype());
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
