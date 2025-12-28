package io.github.tootertutor.ModularPacks.listeners;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.item.Keys;

public final class BackpackUseListener implements Listener {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;

    public BackpackUseListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BackpackMenuRenderer(plugin);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK)
            return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta())
            return;

        Keys keys = plugin.keys();
        var pdc = item.getItemMeta().getPersistentDataContainer();

        String idStr = pdc.get(keys.BACKPACK_ID, PersistentDataType.STRING);
        String typeId = pdc.get(keys.BACKPACK_TYPE, PersistentDataType.STRING);
        if (idStr == null || typeId == null)
            return;

        UUID backpackId;
        try {
            backpackId = UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            return;
        }

        e.setCancelled(true);
        plugin.repo().ensureBackpackExists(backpackId, typeId, p.getUniqueId(), p.getName());
        renderer.openMenu(p, backpackId, typeId);
    }
}
