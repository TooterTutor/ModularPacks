package io.github.tootertutor.ModularPacks.listeners.backpack;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;

/**
 * Protects placed backpack blocks from vanilla physics/fluid updates that would
 * otherwise pop the player head and orphan plugin state.
 */
public final class PlacedBackpackPhysicsListener implements Listener {

    private final ModularPacksPlugin plugin;

    public PlacedBackpackPhysicsListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD) {
            return;
        }

        if (plugin.placedBackpacks().isPlacedAt(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        if (toBlock.getType() != Material.PLAYER_HEAD) {
            return;
        }

        if (plugin.placedBackpacks().isPlacedAt(toBlock.getLocation())) {
            event.setCancelled(true);
        }
    }
}