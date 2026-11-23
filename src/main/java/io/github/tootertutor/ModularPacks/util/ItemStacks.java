package io.github.tootertutor.ModularPacks.util;

import org.bukkit.inventory.ItemStack;

public final class ItemStacks {

    private ItemStacks() {
    }

    public static boolean isAir(ItemStack item) {
        return io.github.tootertutor.ModularCore.util.ItemStacks.isAir(item);
    }

    public static boolean isNotAir(ItemStack item) {
        return io.github.tootertutor.ModularCore.util.ItemStacks.isNotAir(item);
    }
}
