package io.github.tootertutor.ModularPacks.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.item.Keys;

public final class PreventNestingListener implements Listener {

    private final ModularPacksPlugin plugin;

    public PreventNestingListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof BackpackMenuHolder))
            return;

        // While a backpack GUI is open, prevent moving ANY backpacks around at all
        // (including among the player's own inventory) to avoid client automation
        // dupes/desync.
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();

        if (isBackpack(cursor) || isBackpack(current) || isHotbarBackpackSwap(e)) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof BackpackMenuHolder))
            return;

        // Dragging a backpack stack while the GUI is open is always blocked.
        if (isBackpack(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }

        // Also block if any placed item is a backpack (should be rare).
        for (ItemStack it : e.getNewItems().values()) {
            if (isBackpack(it)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof BackpackMenuHolder))
            return;
        if (isBackpack(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof BackpackMenuHolder))
            return;
        if (isBackpack(e.getMainHandItem()) || isBackpack(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    private boolean isHotbarBackpackSwap(InventoryClickEvent e) {
        int btn = e.getHotbarButton();
        if (btn < 0)
            return false;
        if (!(e.getWhoClicked() instanceof Player p))
            return false;
        PlayerInventory inv = p.getInventory();
        ItemStack hotbar = inv.getItem(btn);
        return isBackpack(hotbar);
    }

    private boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        Keys keys = plugin.keys();
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(keys.BACKPACK_ID, PersistentDataType.STRING)
                && pdc.has(keys.BACKPACK_TYPE, PersistentDataType.STRING);
    }
}
