package io.github.tootertutor.ModularPacks.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.item.Keys;

/**
 * Prevents module items from being used as their underlying material (e.g.
 * water bucket placing water, milk bucket drinking, XP bottle throwing, etc.).
 */
public final class PreventModuleUseListener implements Listener {

    private final ModularPacksPlugin plugin;

    public PreventModuleUseListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (isModuleItem(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        ItemStack inHand = e.getPlayer().getInventory().getItem(e.getHand());
        if (isModuleItem(inHand)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        ItemStack inHand = e.getPlayer().getInventory().getItem(e.getHand());
        if (isModuleItem(inHand)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (isModuleItem(e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (isBundleAction(e.getAction()) && (isModuleBundle(current) || isModuleBundle(cursor))) {
            e.setCancelled(true);
            return;
        }

        if (!isModuleBundle(current))
            return;

        InventoryAction action = e.getAction();
        if (action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR) {
            if (cursor != null && !cursor.getType().isAir()) {
                e.setCancelled(true);
            }
            return;
        }

        if (action == InventoryAction.HOTBAR_SWAP) {
            int btn = e.getHotbarButton();
            if (btn >= 0 && e.getWhoClicked().getInventory().getItem(btn) != null) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        ItemStack cursor = e.getOldCursor();
        if (cursor == null || cursor.getType().isAir())
            return;

        for (int raw : e.getRawSlots()) {
            ItemStack slotItem = e.getView().getItem(raw);
            if (isModuleBundle(slotItem)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private boolean isModuleItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Keys keys = plugin.keys();
        return pdc.has(keys.MODULE_ID, PersistentDataType.STRING)
                && pdc.has(keys.MODULE_TYPE, PersistentDataType.STRING);
    }

    private boolean isModuleBundle(ItemStack item) {
        if (!isModuleItem(item))
            return false;
        return item.getItemMeta() instanceof BundleMeta;
    }

    private boolean isBundleAction(InventoryAction action) {
        return action == InventoryAction.PICKUP_FROM_BUNDLE
                || action == InventoryAction.PICKUP_ALL_INTO_BUNDLE
                || action == InventoryAction.PICKUP_SOME_INTO_BUNDLE
                || action == InventoryAction.PLACE_FROM_BUNDLE
                || action == InventoryAction.PLACE_ALL_INTO_BUNDLE
                || action == InventoryAction.PLACE_SOME_INTO_BUNDLE;
    }
}
