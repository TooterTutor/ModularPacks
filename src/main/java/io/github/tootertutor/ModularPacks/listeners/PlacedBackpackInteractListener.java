package io.github.tootertutor.ModularPacks.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.PlacedBackpack;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Handles interactions with placed backpacks.
 * - Right-click to open
 * - Shift-right-click to pick up
 */
public final class PlacedBackpackInteractListener implements Listener {

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    public PlacedBackpackInteractListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractWithPlacedBackpack(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PLAYER_HEAD)
            return;

        Location location = block.getLocation();
        PlacedBackpack placed = plugin.placedBackpacks().getAt(location);

        if (placed == null)
            return;

        // This is a placed backpack - cancel default interaction
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Shift-right-click to pick up
        if (player.isSneaking()) {
            pickupBackpack(player, placed, block);
            return;
        }

        // Regular right-click to open (only with empty hand or non-backpack item)
        openPlacedBackpack(player, placed);
    }

    private void openPlacedBackpack(Player player, PlacedBackpack placed) {
        // Check if player has permission to open
        if (!player.hasPermission("modularpacks.open")) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.open.no_permission",
                    "&cYou don't have permission to open backpacks.")));
            return;
        }

        // Load backpack data
        BackpackData data = plugin.repo().loadOrCreate(placed.backpackId(), placed.backpackType());
        if (data == null) {
            player.sendMessage(
                    Text.c(plugin.lang().get("backpack.open.error", "&cFailed to load backpack data.")));
            return;
        }

        // Try to lock the backpack to this viewer
        boolean locked = plugin.sessions().tryLock(player, placed.backpackId(), false);
        if (!locked) {
            String lockedToName = plugin.sessions().lockedToName(placed.backpackId());
            if (lockedToName != null) {
                player.sendMessage(Text
                        .c(plugin.lang().get("backpack.open.locked", "&cThis backpack is currently open by {player}.")
                                .replace("{player}", lockedToName)));
            } else {
                player.sendMessage(Text.c(plugin.lang().get("backpack.open.failed", "&cCannot open backpack.")));
            }
            return;
        }

        // Get backpack type definition
        BackpackTypeDef typeDef = plugin.cfg().findType(placed.backpackType());
        if (typeDef == null) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.open.invalid_type", "&cInvalid backpack type.")));
            plugin.sessions().onRelatedInventoryClose(player, placed.backpackId());
            return;
        }

        // Open the backpack GUI (page 0 = first page)
        plugin.getBackpackMenuRenderer().openMenu(player, data, typeDef, 0);

    }

    private void pickupBackpack(Player player, PlacedBackpack placed, Block block) {
        // Check if player has permission to pick up
        if (!player.hasPermission("modularpacks.pickup")) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.pickup.no_permission",
                    "&cYou don't have permission to pick up backpacks.")));
            return;
        }

        // Check if backpack is currently open
        if (plugin.sessions().lockedTo(placed.backpackId()) != null) {
            String lockedToName = plugin.sessions().lockedToName(placed.backpackId());
            player.sendMessage(Text.c(plugin.lang()
                    .get("backpack.pickup.in_use", "&cCannot pick up - backpack is being used by {player}.")
                    .replace("{player}", lockedToName != null ? lockedToName : "someone")));
            return;
        }

        // Load backpack data
        BackpackData data = plugin.repo().loadOrCreate(placed.backpackId(), placed.backpackType());
        if (data == null) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.pickup.error", "&cFailed to load backpack data.")));
            return;
        }

        // Get backpack type definition
        BackpackTypeDef typeDef = plugin.cfg().findType(placed.backpackType());
        if (typeDef == null) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.pickup.invalid_type", "&cInvalid backpack type.")));
            return;
        }

        // Create the backpack item
        ItemStack backpackItem = backpackItems.createExisting(placed.backpackId(), placed.backpackType());

        // Give the item to the player
        if (!player.getInventory().addItem(backpackItem).isEmpty()) {
            // Inventory full - drop at player's location
            player.getWorld().dropItemNaturally(player.getLocation(), backpackItem);
            player.sendMessage(Text.c(plugin.lang().get("backpack.pickup.inventory_full", "&eInventory full!")));
        }

        // Remove the block
        block.setType(Material.AIR);

        // Remove from placed backpacks manager
        plugin.placedBackpacks().remove(placed.location());

    }
}
