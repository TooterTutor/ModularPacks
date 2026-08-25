package io.github.tootertutor.ModularPacks.listeners.backpack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.gui.BackpackPageMapping;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.storage.StoredStack;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Interprets GUI actions as transactions against logical storage. Rendered
 * inventory items are never read as canonical quantities or identities.
 */
final class BackpackVirtualStorageController {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;
    private final BackpackSaveManager saveManager;

    BackpackVirtualStorageController(ModularPacksPlugin plugin, BackpackMenuRenderer renderer,
            BackpackSaveManager saveManager) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.saveManager = saveManager;
    }

    void handleStorageClick(InventoryClickEvent event, Player player, BackpackMenuHolder holder, int visibleSlot) {
        event.setCancelled(true);
        int logicalSlot = BackpackPageMapping.logicalIndex(
                holder.paginated(), holder.page(), visibleSlot, holder.logicalSlots());
        if (logicalSlot < 0) {
            return;
        }

        BackpackStorage storage = plugin.backpackStorage().load(holder.data(), holder.logicalSlots());
        ClickType click = event.getClick();

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || click.isShiftClick()) {
            shiftToPlayer(player, holder, storage, logicalSlot);
            return;
        }
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            dropFromSlot(player, holder, storage, logicalSlot, click == ClickType.DROP);
            return;
        }
        if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND
                || event.getAction() == InventoryAction.HOTBAR_SWAP) {
            swapWithEquipmentSlot(event, player, holder, storage, logicalSlot);
            return;
        }

        if (click.isRightClick()) {
            rightClick(player, holder, storage, logicalSlot);
        } else if (click.isLeftClick()) {
            leftClick(player, holder, storage, logicalSlot);
        }
    }

    void handleCollectToCursor(InventoryClickEvent event, Player player, BackpackMenuHolder holder) {
        event.setCancelled(true);
        ItemStack cursor = player.getItemOnCursor();
        if (ItemStacks.isAir(cursor)) {
            return;
        }

        int room = cursor.getMaxStackSize() - cursor.getAmount();
        if (room <= 0) {
            return;
        }

        BackpackStorage storage = plugin.backpackStorage().load(holder.data(), holder.logicalSlots());
        long extracted = plugin.backpackStorage().extractMatching(storage, cursor, room);
        if (extracted <= 0) {
            return;
        }

        ItemStack updated = cursor.clone();
        updated.setAmount(Math.addExact(updated.getAmount(), Math.toIntExact(extracted)));
        player.setItemOnCursor(updated);
        saveAndRender(player, holder, storage);
    }

    void handleDrag(
            InventoryDragEvent event,
            Player player,
            BackpackMenuHolder holder,
            int visibleStorage) {

        List<Integer> visibleTargets = new ArrayList<>();
        int topSize = event.getView().getTopInventory().getSize();

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                // Mixed backpack/player-inventory drags are rejected.
                event.setCancelled(true);
                return;
            }

            if (rawSlot >= 0 && rawSlot < visibleStorage) {
                visibleTargets.add(rawSlot);
            }
        }

        if (visibleTargets.isEmpty()) {
            return;
        }

        event.setCancelled(true);

        ItemStack oldCursor = event.getOldCursor();
        if (ItemStacks.isAir(oldCursor)) {
            return;
        }

        ItemStack originalCursor = oldCursor.clone();
        List<Integer> targets = List.copyOf(visibleTargets);
        DragType dragType = event.getType();

        UUID backpackId = holder.backpackId();
        int page = holder.page();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            Inventory top = player.getOpenInventory().getTopInventory();

            if (!(top.getHolder() instanceof BackpackMenuHolder openHolder)) {
                player.updateInventory();
                return;
            }

            if (!openHolder.backpackId().equals(backpackId)
                    || openHolder.page() != page) {
                player.updateInventory();
                return;
            }

            ItemStack currentCursor = player.getItemOnCursor();

            if (ItemStacks.isAir(currentCursor)
                    || currentCursor.getAmount() != originalCursor.getAmount()
                    || !plugin.backpackStorage().identity()
                            .sameIdentity(currentCursor, originalCursor)) {

                player.updateInventory();
                return;
            }

            BackpackStorage storage = plugin.backpackStorage().load(
                    openHolder.data(),
                    openHolder.logicalSlots());

            long originalAmount = originalCursor.getAmount();
            long remaining = originalAmount;

            for (int index = 0; index < targets.size() && remaining > 0; index++) {

                int logicalSlot = BackpackPageMapping.logicalIndex(
                        openHolder.paginated(),
                        openHolder.page(),
                        targets.get(index),
                        openHolder.logicalSlots());

                if (logicalSlot < 0) {
                    continue;
                }

                long requested;

                if (dragType == DragType.SINGLE) {
                    requested = 1;
                } else {
                    int targetsLeft = targets.size() - index;
                    requested = Math.max(1, remaining / targetsLeft);
                }

                requested = Math.min(requested, remaining);

                long inserted = plugin.backpackStorage().insertIntoSlot(
                        openHolder.data(),
                        storage,
                        logicalSlot,
                        originalCursor,
                        requested);

                remaining -= inserted;
            }

            long inserted = originalAmount - remaining;

            if (inserted <= 0) {
                player.updateInventory();
                return;
            }

            /*
             * Item-conservation sanity check.
             */
            if (inserted + remaining != originalAmount) {
                plugin.getLogger().severe(
                        "Virtual backpack drag violated item conservation for "
                                + player.getName());

                player.updateInventory();
                return;
            }

            ItemStack remainder = remaining == 0
                    ? null
                    : plugin.backpackStorage().materialize(
                            originalCursor,
                            Math.toIntExact(remaining));

            plugin.backpackStorage().save(openHolder.data(), storage);
            player.setItemOnCursor(remainder);

            renderer.render(openHolder);
            saveManager.markInteraction(player, openHolder);

            player.updateInventory();
        });
    }

    private void leftClick(Player player, BackpackMenuHolder holder, BackpackStorage storage, int slot) {
        ItemStack cursor = player.getItemOnCursor();
        StoredStack stored = storage.get(slot);

        if (ItemStacks.isAir(cursor)) {
            if (stored == null) {
                return;
            }
            ItemStack extracted = plugin.backpackStorage().extractFromSlot(
                    storage, slot, stored.prototype().getMaxStackSize());
            player.setItemOnCursor(extracted);
            saveAndRender(player, holder, storage);
            return;
        }

        if (stored == null || plugin.backpackStorage().identity().sameIdentity(stored, cursor)) {
            insertCursor(player, holder, storage, slot, cursor, cursor.getAmount());
            return;
        }
        swapCursor(player, holder, storage, slot, cursor, stored);
    }

    private void rightClick(Player player, BackpackMenuHolder holder, BackpackStorage storage, int slot) {
        ItemStack cursor = player.getItemOnCursor();
        StoredStack stored = storage.get(slot);

        if (ItemStacks.isAir(cursor)) {
            if (stored == null) {
                return;
            }
            long halfRoundedUp = stored.count() / 2 + stored.count() % 2;
            int requested = Math.toIntExact(Math.min(halfRoundedUp, stored.prototype().getMaxStackSize()));
            player.setItemOnCursor(plugin.backpackStorage().extractFromSlot(storage, slot, requested));
            saveAndRender(player, holder, storage);
            return;
        }

        if (stored == null || plugin.backpackStorage().identity().sameIdentity(stored, cursor)) {
            insertCursor(player, holder, storage, slot, cursor, 1);
            return;
        }
        swapCursor(player, holder, storage, slot, cursor, stored);
    }

    private void insertCursor(Player player, BackpackMenuHolder holder, BackpackStorage storage, int slot,
            ItemStack cursor, long requested) {
        long inserted = plugin.backpackStorage().insertIntoSlot(
                holder.data(), storage, slot, cursor, requested);
        if (inserted <= 0) {
            return;
        }
        long remaining = cursor.getAmount() - inserted;
        player.setItemOnCursor(remaining == 0
                ? null
                : plugin.backpackStorage().materialize(cursor, Math.toIntExact(remaining)));
        saveAndRender(player, holder, storage);
    }

    private void swapCursor(Player player, BackpackMenuHolder holder, BackpackStorage storage, int slot,
            ItemStack cursor, StoredStack stored) {
        long oldVanillaCapacity = stored.prototype().getMaxStackSize();
        long newLogicalCapacity = plugin.backpackStorage().capacityFor(holder.data(), cursor);
        if (stored.count() > oldVanillaCapacity || cursor.getAmount() > newLogicalCapacity) {
            player.sendMessage(Text.c("&cThat virtual stack cannot be swapped as one item stack."));
            return;
        }

        ItemStack extracted = plugin.backpackStorage().materialize(stored, Math.toIntExact(stored.count()));
        storage.set(slot, new StoredStack(cursor, cursor.getAmount()));
        player.setItemOnCursor(extracted);
        saveAndRender(player, holder, storage);
    }

    private void shiftToPlayer(Player player, BackpackMenuHolder holder, BackpackStorage storage, int slot) {
        StoredStack stored = storage.get(slot);
        if (stored == null) {
            return;
        }

        ItemStack prototype = stored.prototype();
        long remaining = stored.count();
        while (remaining > 0) {
            int chunkAmount = Math.toIntExact(Math.min(remaining, prototype.getMaxStackSize()));
            ItemStack chunk = plugin.backpackStorage().materialize(prototype, chunkAmount);
            var leftovers = player.getInventory().addItem(chunk);
            int rejected = leftovers.values().stream().mapToInt(stack -> stack.getAmount()).sum();
            int moved = chunkAmount - rejected;
            if (moved <= 0) {
                break;
            }
            remaining -= moved;
            if (rejected > 0) {
                break;
            }
        }

        if (remaining == stored.count()) {
            return;
        }
        if (remaining == 0) {
            storage.clear(slot);
        } else {
            storage.set(slot, stored.withCount(remaining));
        }
        saveAndRender(player, holder, storage);
    }

    private void dropFromSlot(Player player, BackpackMenuHolder holder, BackpackStorage storage, int slot,
            boolean oneItem) {
        StoredStack stored = storage.get(slot);
        if (stored == null) {
            return;
        }
        int amount = oneItem ? 1 : stored.prototype().getMaxStackSize();
        ItemStack dropped = plugin.backpackStorage().extractFromSlot(storage, slot, amount);
        if (dropped == null) {
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        saveAndRender(player, holder, storage);
    }

    private void swapWithEquipmentSlot(InventoryClickEvent event, Player player, BackpackMenuHolder holder,
            BackpackStorage storage, int slot) {
        boolean offhand = event.getClick() == ClickType.SWAP_OFFHAND;
        int hotbarSlot = event.getHotbarButton();
        if (!offhand && (hotbarSlot < 0 || hotbarSlot > 8)) {
            return;
        }

        ItemStack source = offhand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItem(hotbarSlot);
        StoredStack stored = storage.get(slot);

        if (ItemStacks.isNotAir(source)
                && (!plugin.cfg().isAllowedInBackpack(source) || isBackpack(source))) {
            return;
        }

        if (stored != null && stored.count() > stored.prototype().getMaxStackSize()) {
            player.sendMessage(Text.c("&cThat virtual stack cannot be moved into a hotbar or offhand slot at once."));
            return;
        }
        if (ItemStacks.isNotAir(source)
                && source.getAmount() > plugin.backpackStorage().capacityFor(holder.data(), source)) {
            return;
        }

        ItemStack targetItem = stored == null ? null
                : plugin.backpackStorage().materialize(stored, Math.toIntExact(stored.count()));
        if (ItemStacks.isAir(source)) {
            storage.clear(slot);
        } else {
            storage.set(slot, new StoredStack(source, source.getAmount()));
        }

        if (offhand) {
            player.getInventory().setItemInOffHand(targetItem);
        } else {
            player.getInventory().setItem(hotbarSlot, targetItem);
        }
        saveAndRender(player, holder, storage);
    }

    private boolean isBackpack(ItemStack item) {
        if (ItemStacks.isAir(item) || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(plugin.keys().BACKPACK_ID, PersistentDataType.STRING);
    }

    private void saveAndRender(Player player, BackpackMenuHolder holder, BackpackStorage storage) {
        plugin.backpackStorage().save(holder.data(), storage);
        renderer.render(holder);
        saveManager.markInteraction(player, holder);
    }
}
