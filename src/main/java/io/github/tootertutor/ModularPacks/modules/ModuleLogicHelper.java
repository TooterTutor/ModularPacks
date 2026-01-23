package io.github.tootertutor.ModularPacks.modules;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.util.ItemStacks;

/**
 * Utility class with common helper methods for module logic classes.
 * Provides reusable patterns for crafting-style interactions to reduce code
 * duplication.
 */
public final class ModuleLogicHelper {

    private ModuleLogicHelper() {
    }

    /**
     * Decrement a stack by one, returning null if it becomes empty.
     */
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

    /**
     * Check if an item stack is empty or null.
     */
    public static boolean isEmpty(ItemStack s) {
        return ItemStacks.isAir(s);
    }

    /**
     * Standard logic for crafting once to cursor.
     * Handles cursor management, similarity checks, and stack size validation.
     * 
     * @param player        The player crafting
     * @param result        The result item to add to cursor
     * @param consumeInputs Callback to consume the input items
     */
    public static void standardCraftOnceToCursor(Player player, ItemStack result, Runnable consumeInputs) {
        if (isEmpty(result))
            return;

        ItemStack cursor = player.getItemOnCursor();
        if (ItemStacks.isNotAir(cursor)) {
            if (!cursor.isSimilar(result))
                return;
            int space = cursor.getMaxStackSize() - cursor.getAmount();
            if (space < result.getAmount())
                return;
            cursor = cursor.clone();
            cursor.setAmount(cursor.getAmount() + result.getAmount());
        } else {
            cursor = result.clone();
        }

        player.setItemOnCursor(cursor);
        consumeInputs.run();
    }

    /**
     * Standard logic for shift-click crafting.
     * Crafts up to maxIterations times, adding results to player inventory.
     * 
     * @param player        The player crafting
     * @param maxIterations Maximum number of crafts (typically 64)
     * @param getResult     Supplier that computes the current craft result
     * @param consumeInputs Callback to consume the input items for one craft
     */
    public static void standardCraftShift(
            Player player,
            int maxIterations,
            java.util.function.Supplier<ItemStack> getResult,
            Runnable consumeInputs) {
        for (int i = 0; i < maxIterations; i++) {
            ItemStack result = getResult.get();
            if (isEmpty(result))
                return;

            var leftovers = player.getInventory().addItem(result.clone());
            if (!leftovers.isEmpty())
                return;

            consumeInputs.run();
        }
    }
}
