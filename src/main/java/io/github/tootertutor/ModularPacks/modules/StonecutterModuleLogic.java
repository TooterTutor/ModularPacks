package io.github.tootertutor.ModularPacks.modules;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.ModularPacks.listeners.ModuleClickHandler;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class StonecutterModuleLogic {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;

    private StonecutterModuleLogic() {
    }

    public static void updateResult(Inventory inv) {
        if (inv == null || inv.getSize() < 2)
            return;

        ItemStack input = inv.getItem(INPUT_SLOT);
        if (ItemStacks.isAir(input)) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        StonecuttingRecipe recipe = findFirstMatch(input);
        if (recipe == null) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        ItemStack result = recipe.getResult();
        if (ItemStacks.isAir(result)) {
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
        ModuleLogicHelper.standardCraftShift(player, 64, () -> {
            ItemStack input = inv.getItem(INPUT_SLOT);
            if (ItemStacks.isAir(input))
                return null;
            StonecuttingRecipe recipe = findFirstMatch(input);
            if (recipe == null)
                return null;
            return recipe.getResult();
        }, () -> {
            ItemStack input = inv.getItem(INPUT_SLOT);
            inv.setItem(INPUT_SLOT, ModuleLogicHelper.decrementOne(input));
        });
    }

    private static void craftOnceToCursor(Player player, Inventory inv) {
        ItemStack input = inv.getItem(INPUT_SLOT);
        if (ItemStacks.isAir(input))
            return;

        StonecuttingRecipe recipe = findFirstMatch(input);
        if (recipe == null)
            return;

        ItemStack result = recipe.getResult();

        ModuleLogicHelper.standardCraftOnceToCursor(player, result, () -> {
            inv.setItem(INPUT_SLOT, ModuleLogicHelper.decrementOne(input));
        });
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

}
