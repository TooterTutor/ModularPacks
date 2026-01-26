package io.github.tootertutor.ModularPacks.listeners;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Handles placing backpacks as blocks in the world.
 * Players shift-right-click on a block with a backpack to place it.
 */
public final class BackpackPlacementListener implements Listener {

    private final ModularPacksPlugin plugin;

    public BackpackPlacementListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        new BackpackItems(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceBackpack(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Player player = event.getPlayer();
        if (!player.isSneaking())
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemStacks.isAir(item))
            return;

        // Check if this is a backpack
        if (!item.hasItemMeta())
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        Keys keys = plugin.keys();
        if (!meta.getPersistentDataContainer().has(keys.BACKPACK_ID, PersistentDataType.STRING))
            return;

        if (!meta.getPersistentDataContainer().has(keys.BACKPACK_TYPE, PersistentDataType.STRING))
            return;

        // Check if placeable feature is enabled
        if (!plugin.cfg().isPlaceableEnabled()) {
            player.sendMessage(
                    Text.c(plugin.lang().get("backpack.placement.disabled", "&cPlaceable backpacks are disabled")));
            return;
        }

        // Check permissions
        if (!player.hasPermission("modularpacks.place")) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.placement.no_permission",
                    "&cYou don't have permission to place backpacks.")));
            return;
        }

        // Get backpack data
        String backpackIdStr = meta.getPersistentDataContainer().get(keys.BACKPACK_ID, PersistentDataType.STRING);
        String backpackType = meta.getPersistentDataContainer().get(keys.BACKPACK_TYPE, PersistentDataType.STRING);

        if (backpackIdStr == null || backpackType == null) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.placement.invalid", "&cInvalid backpack data.")));
            return;
        }

        UUID backpackId = UUID.fromString(backpackIdStr);

        // Check if backpack is already open
        if (plugin.sessions().lockedTo(backpackId) != null) {
            player.sendMessage(
                    Text.c(plugin.lang().get("backpack.placement.in_use", "&cThis backpack is currently in use.")));
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null)
            return;

        BlockFace face = event.getBlockFace();
        Block targetBlock = clickedBlock.getRelative(face);

        // Check if the target location is replaceable
        if (!targetBlock.getType().isAir() && targetBlock.getType() != Material.WATER &&
                targetBlock.getType() != Material.LAVA && !targetBlock.isReplaceable()) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.placement.blocked",
                    "&cCannot place backpack there - space is occupied.")));
            return;
        }

        // Get the placement location
        Location placementLoc = targetBlock.getLocation();

        // Check if another backpack is already at this location
        if (plugin.placedBackpacks().isPlacedAt(placementLoc)) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.placement.occupied",
                    "&cA backpack is already placed at this location.")));
            return;
        }

        // Load backpack data to ensure it exists
        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
        if (data == null) {
            player.sendMessage(Text.c(plugin.lang().get("backpack.placement.error", "&cFailed to place backpack.")));
            return;
        }

        // Place the backpack
        boolean success = plugin.placedBackpacks().place(placementLoc, backpackId, backpackType, player);
        if (!success) {
            player.sendMessage(Text.c(
                    plugin.lang().get("backpack.placement.failed", "&cAn error occurred while placing the backpack.")));
            return;
        }

        // Remove the item from player's hand
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(item);
        }

        // Cancel the event to prevent normal block placement
        event.setCancelled(true);
    }
}
