package io.github.tootertutor.ModularPacks.listeners;

import java.util.Iterator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.PlacedBackpack;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Handles breaking placed backpacks.
 * Returns the backpack item with all contents intact.
 */
public final class PlacedBackpackBreakListener implements Listener {

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    public PlacedBackpackBreakListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakPlacedBackpack(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD)
            return;

        Location location = block.getLocation();
        PlacedBackpack placed = plugin.placedBackpacks().getAt(location);

        if (placed == null)
            return;

        // This is a placed backpack
        Player player = event.getPlayer();

        // Check if backpack is currently open
        if (plugin.sessions().lockedTo(placed.backpackId()) != null) {
            String lockedToName = plugin.sessions().lockedToName(placed.backpackId());
            player.sendMessage(Text.c(
                    plugin.lang().get("backpack.break.in_use", "&cCannot break - backpack is being used by {player}.")
                            .replace("{player}", lockedToName != null ? lockedToName : "someone")));
            event.setCancelled(true);
            return;
        }

        // Load backpack data
        BackpackData data = plugin.repo().loadOrCreate(placed.backpackId(), placed.backpackType());
        if (data == null) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.break.error", "&cFailed to load backpack data.")));
            event.setCancelled(true);
            return;
        }

        // Get backpack type definition
        BackpackTypeDef typeDef = plugin.cfg().findType(placed.backpackType());
        if (typeDef == null) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.break.invalid_type", "&cInvalid backpack type.")));
            event.setCancelled(true);
            return;
        }

        // Cancel the default drop
        event.setDropItems(false);

        // Create the backpack item
        ItemStack backpackItem = backpackItems.createExisting(placed.backpackId(), placed.backpackType());

        // Drop the backpack item
        if (player.getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(location, backpackItem);
        }

        // Remove from placed backpacks manager
        plugin.placedBackpacks().remove(location);

    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Protect placed backpacks from explosions
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block.getType() != Material.PLAYER_HEAD)
                continue;

            PlacedBackpack placed = plugin.placedBackpacks().getAt(block.getLocation());
            if (placed != null) {
                // Remove from explosion list - don't destroy placed backpacks
                iterator.remove();

                // Optionally: drop the backpack item
                if (plugin.cfg().shouldDropPlacedBackpacksOnExplosion()) {
                    BackpackData data = plugin.repo().loadOrCreate(placed.backpackId(), placed.backpackType());
                    BackpackTypeDef typeDef = plugin.cfg().findType(placed.backpackType());

                    if (data != null && typeDef != null) {
                        ItemStack backpackItem = backpackItems.createExisting(placed.backpackId(),
                                placed.backpackType());
                        block.getWorld().dropItemNaturally(block.getLocation(), backpackItem);
                        block.setType(Material.AIR);
                        plugin.placedBackpacks().remove(placed.location());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        // Protect placed backpacks from block explosions (beds, respawn anchors, etc.)
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block.getType() != Material.PLAYER_HEAD)
                continue;

            PlacedBackpack placed = plugin.placedBackpacks().getAt(block.getLocation());
            if (placed != null) {
                // Remove from explosion list - don't destroy placed backpacks
                iterator.remove();

                // Optionally: drop the backpack item
                if (plugin.cfg().shouldDropPlacedBackpacksOnExplosion()) {
                    BackpackData data = plugin.repo().loadOrCreate(placed.backpackId(), placed.backpackType());
                    BackpackTypeDef typeDef = plugin.cfg().findType(placed.backpackType());

                    if (data != null && typeDef != null) {
                        ItemStack backpackItem = backpackItems.createExisting(placed.backpackId(),
                                placed.backpackType());
                        block.getWorld().dropItemNaturally(block.getLocation(), backpackItem);
                        block.setType(Material.AIR);
                        plugin.placedBackpacks().remove(placed.location());
                    }
                }
            }
        }
    }
}
