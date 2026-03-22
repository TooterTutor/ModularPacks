package io.github.tootertutor.ModularPacks.listeners.backpack;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;

/**
 * Reconciles placed backpack renders when chunks load.
 */
public final class PlacedBackpackChunkLoadListener implements Listener {

    private final ModularPacksPlugin plugin;

    public PlacedBackpackChunkLoadListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.placedBackpacks().refreshRendersInChunk(
                event.getWorld(),
                event.getChunk().getX(),
                event.getChunk().getZ());
    }
}
