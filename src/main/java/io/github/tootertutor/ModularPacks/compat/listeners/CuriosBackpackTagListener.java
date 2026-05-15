package io.github.tootertutor.ModularPacks.compat.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.compat.CuriosItemTagger;

public class CuriosBackpackTagListener implements Listener {

    private final ModularPacksPlugin plugin;

    public CuriosBackpackTagListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (isBackpackItem(cursor)) {
            CuriosItemTagger.syncBackpackTags(plugin, cursor);
        }

        if (isBackpackItem(current)) {
            CuriosItemTagger.syncBackpackTags(plugin, current);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!isBackpackItem(item)) {
            return;
        }

        CuriosItemTagger.syncBackpackTags(plugin, item);
    }

    private boolean isBackpackItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }

        String backpackId = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING);

        return backpackId != null && !backpackId.isEmpty();
    }

}
