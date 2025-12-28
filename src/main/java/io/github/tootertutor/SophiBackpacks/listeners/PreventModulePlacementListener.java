package io.github.tootertutor.SophiBackpacks.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;
import io.github.tootertutor.SophiBackpacks.item.Keys;
import io.github.tootertutor.SophiBackpacks.text.Text;

public final class PreventModulePlacementListener implements Listener {

    private final SophiBackpacksPlugin plugin;

    public PreventModulePlacementListener(SophiBackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isModuleItem(e.getItemInHand())) {
            cancel(e.getPlayer());
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent e) {
        if (isModuleItem(e.getItemInHand())) {
            cancel(e.getPlayer());
            e.setCancelled(true);
        }
    }

    private void cancel(Player player) {
        player.sendMessage(Text.c(plugin.lang().get("errors.modulePlaceBlocked",
                "&cYou can't place modules in the world.")));
    }

    private boolean isModuleItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Keys keys = plugin.keys();
        return pdc.has(keys.MODULE_ID, PersistentDataType.STRING)
                && pdc.has(keys.MODULE_TYPE, PersistentDataType.STRING);
    }
}

