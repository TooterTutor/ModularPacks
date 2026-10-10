package io.github.tootertutor.ModularPacks.listeners.backpack;

import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

/** Adapts Bukkit interaction state to the backpack gesture rules. */
final class BackpackInteractionRouter {
    static BackpackInteractionPriority.Action resolve(ModularPacksPlugin plugin, PlayerInteractEvent event) {
        var player = event.getPlayer();
        var block = event.getClickedBlock();
        BackpackItems items = new BackpackItems(plugin);
        return BackpackInteractionPriority.resolve(
                classify(player.getInventory().getItemInMainHand(), items),
                classify(player.getInventory().getItemInOffHand(), items),
                event.getHand() == EquipmentSlot.HAND, player.isSneaking(), block != null,
                block != null && plugin.placedBackpacks().getAt(block.getLocation()) != null);
    }

    private static BackpackInteractionPriority.Item classify(ItemStack item, BackpackItems items) {
        if (ItemStacks.isAir(item)) {
            return BackpackInteractionPriority.Item.EMPTY;
        }
        return items.isBackpack(item) ? BackpackInteractionPriority.Item.BACKPACK
                : BackpackInteractionPriority.Item.OTHER;
    }

    private BackpackInteractionRouter() {
    }
}
