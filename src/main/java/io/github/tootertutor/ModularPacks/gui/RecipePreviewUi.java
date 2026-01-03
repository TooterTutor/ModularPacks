package io.github.tootertutor.ModularPacks.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import net.kyori.adventure.text.Component;

public final class RecipePreviewUi {

    public enum Kind {
        CRAFTING,
        SMITHING
    }

    private RecipePreviewUi() {
    }

    public static void openCrafting(ModularPacksPlugin plugin, Player player, Component title, ItemStack[] grid3x3,
            ItemStack result) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        RecipePreviewHolder holder = new RecipePreviewHolder(Kind.CRAFTING);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.WORKBENCH,
                title == null ? Component.text("Recipe Preview") : title);
        holder.setInventory(inv);

        for (int i = 0; i < 9; i++) {
            ItemStack it = (grid3x3 != null && i < grid3x3.length) ? grid3x3[i] : null;
            inv.setItem(1 + i, it);
        }

        inv.setItem(0, result == null ? null : result.clone());

        player.openInventory(inv);
    }

    public static void openSmithing(ModularPacksPlugin plugin, Player player, Component title,
            ItemStack template, ItemStack base, ItemStack addition, ItemStack result) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        RecipePreviewHolder holder = new RecipePreviewHolder(Kind.SMITHING);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.SMITHING,
                title == null ? Component.text("Recipe Preview") : title);
        holder.setInventory(inv);

        // SmithingInventory is [0]=template, [1]=base, [2]=addition, [3]=result.
        inv.setItem(0, template == null ? null : template.clone());
        inv.setItem(1, base == null ? null : base.clone());
        inv.setItem(2, addition == null ? null : addition.clone());
        inv.setItem(3, result == null ? null : result.clone());

        player.openInventory(inv);
    }
}
