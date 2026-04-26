package io.github.tootertutor.ModularPacks.listeners.backpack;

import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
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

        // From this point on, this interaction is explicitly a backpack placement
        // attempt. Deny vanilla handling so blocked placements don't fall through and
        // place a normal head item.
        event.setUseItemInHand(Result.DENY);
        event.setUseInteractedBlock(Result.DENY);
        event.setCancelled(true);

        // Check if placeable feature is enabled
        if (!plugin.cfg().isPlaceableEnabled()) {
            player.sendMessage(
                    Text.c(plugin.lang().get(player, "backpack.placement.disabled",
                            "&cPlaceable backpacks are disabled")));
            return;
        }

        // Check permissions
        if (!player.hasPermission("modularpacks.place")) {
            player.sendMessage(Text.c(plugin.lang().get(player, "backpack.placement.no_permission",
                    "&cYou don't have permission to place backpacks.")));
            return;
        }

        // Get backpack data
        String backpackIdStr = meta.getPersistentDataContainer().get(keys.BACKPACK_ID, PersistentDataType.STRING);
        String backpackType = meta.getPersistentDataContainer().get(keys.BACKPACK_TYPE, PersistentDataType.STRING);

        if (backpackIdStr == null || backpackType == null) {
            player.sendMessage(
                    Text.c(plugin.lang().get(player, "backpack.placement.invalid", "&cInvalid backpack data.")));
            return;
        }

        UUID backpackId = UUID.fromString(backpackIdStr);

        // Check if backpack is already open
        if (plugin.sessions().lockedTo(backpackId) != null) {
            player.sendMessage(
                    Text.c(plugin.lang().get(player, "backpack.placement.in_use",
                            "&cThis backpack is currently in use.")));
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null)
            return;

        BlockFace face = event.getBlockFace();

        // If the clicked block itself is replaceable (snow layer, grass, flowers, etc.)
        // use it as the placement target so it gets replaced, matching vanilla
        // behaviour.
        Block targetBlock;
        if (isReplaceableTarget(clickedBlock)) {
            targetBlock = clickedBlock;
        } else {
            targetBlock = clickedBlock.getRelative(face);
        }

        // Skulls cannot be waterlogged — block placement inside water or lava.
        Material targetMat = targetBlock.getType();
        if (targetMat == Material.WATER || targetMat == Material.LAVA
                || targetMat == Material.BUBBLE_COLUMN) {
            player.sendMessage(Text.c(plugin.lang().get(player, "backpack.placement.blocked",
                    "&cCannot place backpack there - space is occupied.")));
            return;
        }

        // Check if the target location is free
        if (!targetBlock.getType().isAir() && !targetBlock.isReplaceable()) {
            player.sendMessage(Text.c(plugin.lang().get(player, "backpack.placement.blocked",
                    "&cCannot place backpack there - space is occupied.")));
            return;
        }

        // Reject placements where a floor head cannot survive, otherwise vanilla
        // physics will pop it off immediately and can desync backpack tracking.
        BlockData headData = Material.PLAYER_HEAD.createBlockData();
        if (!targetBlock.canPlace(headData)) {
            player.sendMessage(Text.c(plugin.lang().get(player, "backpack.placement.blocked",
                    "&cCannot place backpack there - space is occupied.")));
            return;
        }

        // Respect external claim/protection plugins that validate block placement.
        if (!canPlaceHere(event, player, item, clickedBlock, targetBlock, headData)) {
            player.sendMessage(Text.c(plugin.lang().get(player, "backpack.placement.blocked",
                    "&cCannot place backpack there - space is occupied.")));
            return;
        }

        // Get the placement location
        Location placementLoc = targetBlock.getLocation();

        // Check if another backpack is already at this location
        if (plugin.placedBackpacks().isPlacedAt(placementLoc)) {
            player.sendMessage(Text.c(plugin.lang().get(player, "backpack.placement.occupied",
                    "&cA backpack is already placed at this location.")));
            return;
        }

        // Load backpack data to ensure it exists
        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
        if (data == null) {
            player.sendMessage(
                    Text.c(plugin.lang().get(player, "backpack.placement.error", "&cFailed to place backpack.")));
            return;
        }

        // Place the backpack
        boolean success = plugin.placedBackpacks().place(placementLoc, backpackId, backpackType, player, item);
        if (!success) {
            player.sendMessage(Text.c(
                    plugin.lang().get(player, "backpack.placement.failed",
                            "&cAn error occurred while placing the backpack.")));
            return;
        }

        // Remove the item from player's hand
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(item);
        }

        // Already cancelled above to block vanilla fallback placement.
    }

    /**
     * Runs build/place checks so protection plugins (claims, regions, etc.) can
     * veto backpack placement the same way they veto normal block placement.
     */
    private boolean canPlaceHere(PlayerInteractEvent interactEvent, Player player, ItemStack item, Block clickedBlock,
            Block targetBlock, BlockData headData) {
        // Run a synthetic BlockPlaceEvent so protection plugins can cancel.
        BlockState replacedState = targetBlock.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                targetBlock,
                replacedState,
                clickedBlock,
                item,
                player,
                true,
                interactEvent.getHand());
        plugin.getServer().getPluginManager().callEvent(placeEvent);
        return placeEvent.canBuild() && !placeEvent.isCancelled();
    }

    /**
     * Returns true if the block should be treated as a replacement target when
     * clicked — i.e. it is in {@code #minecraft:replaceable},
     * {@code #minecraft:replaceable_by_mushrooms}, or
     * {@code #minecraft:replaceable_by_trees}, but is NOT fluid (water/lava are
     * handled separately).
     */
    private boolean isReplaceableTarget(Block block) {
        Material mat = block.getType();
        // Never treat fluids as a replaceable target — those are blocked outright.
        if (mat == Material.WATER || mat == Material.LAVA || mat == Material.BUBBLE_COLUMN) {
            return false;
        }
        if (block.isReplaceable()) {
            return true;
        }
        // Check the two additional vanilla tags that isReplaceable() may not cover.
        Set<Material> byMushrooms = Tag.REPLACEABLE_BY_MUSHROOMS.getValues();
        if (byMushrooms != null && byMushrooms.contains(mat)) {
            return true;
        }
        Set<Material> byTrees = Tag.REPLACEABLE_BY_TREES.getValues();
        return byTrees != null && byTrees.contains(mat);
    }
}
