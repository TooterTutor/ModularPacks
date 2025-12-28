package io.github.tootertutor.SophiBackpacks.modules;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.SophiBackpacks.listeners.ModuleClickHandler;

public final class StonecutterModuleLogic {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;

    private StonecutterModuleLogic() {
    }

    public static void updateResult(Inventory inv) {
        if (inv == null || inv.getSize() < 2)
            return;

        ItemStack input = inv.getItem(INPUT_SLOT);
        if (input == null || input.getType().isAir()) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        StonecuttingRecipe recipe = findFirstMatch(input);
        if (recipe == null) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        ItemStack result = recipe.getResult();
        if (result == null || result.getType().isAir()) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        inv.setItem(OUTPUT_SLOT, result.clone());
    }

    public static boolean handleClick(InventoryClickEvent e, Player player) {
        return handleClick(null, e, player);
    }

    public static boolean handleClick(Plugin plugin, InventoryClickEvent e, Player player) {
        Inventory inv = e.getView().getTopInventory();
        if (inv == null || inv.getSize() < 2)
            return false;

        return ModuleClickHandler.handleOutput(plugin, e, player, OUTPUT_SLOT,
                () -> craftOnceToCursor(player, inv),
                () -> craftShift(player, inv),
                () -> updateResult(inv));
    }

    private static void craftShift(Player player, Inventory inv) {
        for (int i = 0; i < 64; i++) {
            ItemStack input = inv.getItem(INPUT_SLOT);
            if (input == null || input.getType().isAir())
                return;

            StonecuttingRecipe recipe = findFirstMatch(input);
            if (recipe == null)
                return;

            ItemStack out = recipe.getResult();
            if (out == null || out.getType().isAir())
                return;

            var leftovers = player.getInventory().addItem(out.clone());
            if (!leftovers.isEmpty())
                return;

            inv.setItem(INPUT_SLOT, decrementOne(input));
        }
    }

    private static void craftOnceToCursor(Player player, Inventory inv) {
        ItemStack input = inv.getItem(INPUT_SLOT);
        if (input == null || input.getType().isAir())
            return;

        StonecuttingRecipe recipe = findFirstMatch(input);
        if (recipe == null)
            return;

        ItemStack out = recipe.getResult();
        if (out == null || out.getType().isAir())
            return;

        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            if (!cursor.isSimilar(out))
                return;
            int space = cursor.getMaxStackSize() - cursor.getAmount();
            if (space < out.getAmount())
                return;
            cursor = cursor.clone();
            cursor.setAmount(cursor.getAmount() + out.getAmount());
        } else {
            cursor = out.clone();
        }

        player.setItemOnCursor(cursor);
        inv.setItem(INPUT_SLOT, decrementOne(input));
    }

    private static StonecuttingRecipe findFirstMatch(ItemStack input) {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (!(r instanceof StonecuttingRecipe sc))
                continue;
            if (sc.getInputChoice() != null && sc.getInputChoice().test(input))
                return sc;
        }
        return null;
    }

    private static ItemStack decrementOne(ItemStack stack) {
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
