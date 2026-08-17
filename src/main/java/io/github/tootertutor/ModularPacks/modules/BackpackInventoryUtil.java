package io.github.tootertutor.ModularPacks.modules;

import org.bukkit.inventory.ItemStack;

/**
 * Helpers for ordinary module-owned Bukkit stacks. Main backpack storage must use
 * {@code BackpackStorageService} instead.
 */
public final class BackpackInventoryUtil {

    private BackpackInventoryUtil() {
    }

    public static ItemStack decrementOne(ItemStack stack) {
        if (stack == null)
            return null;
        ItemStack s = stack.clone();
        int amt = s.getAmount();
        if (amt <= 1)
            return null;
        s.setAmount(amt - 1);
        return s;
    }

}
