package io.github.tootertutor.ModularPacks.util;

import org.bukkit.inventory.ItemStack;

public final class ItemStacks {

    private ItemStacks() {
    }

    public static boolean isAir(ItemStack item) {
        return item == null || item.getType() == null || item.getType().isAir();
    }

    public static boolean isNotAir(ItemStack item) {
        return !isAir(item);
    }
}
