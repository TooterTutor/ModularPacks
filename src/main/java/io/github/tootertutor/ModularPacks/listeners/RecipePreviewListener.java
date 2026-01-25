package io.github.tootertutor.ModularPacks.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import io.github.tootertutor.ModularPacks.gui.RecipePreviewHolder;

public final class RecipePreviewListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player))
            return;
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof RecipePreviewHolder))
            return;

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player))
            return;
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof RecipePreviewHolder))
            return;

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player))
            return;
        Inventory top = e.getInventory();
        if (top == null || !(top.getHolder() instanceof RecipePreviewHolder))
            return;

        // Ensure nothing can "stick" around (and keeps memory clean).
        top.clear();
    }
}
