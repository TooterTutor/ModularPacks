package io.github.tootertutor.ModularPacks.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.modules.AnvilModuleLogic;

public final class AnvilModuleListener implements Listener {

    private final ModularPacksPlugin plugin;

    public AnvilModuleListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;
        if (!AnvilModuleLogic.hasSession(player))
            return;
        if (e.getView().getTopInventory().getType() != InventoryType.ANVIL)
            return;

        // Optional: prevent putting backpacks into anvil slots during module session
        ItemStack cursor = e.getCursor();
        if (isBackpack(cursor)) {
            int raw = e.getRawSlot();
            if (raw >= 0 && raw < e.getView().getTopInventory().getSize()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player))
            return;
        if (!AnvilModuleLogic.hasSession(player))
            return;
        if (e.getInventory().getType() != InventoryType.ANVIL)
            return;

        AnvilModuleLogic.handleClose(plugin, player, e.getInventory());
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
