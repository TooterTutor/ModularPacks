package io.github.tootertutor.SophiBackpacks.listeners;

import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Shared click/shift-click helpers for module GUIs.
 * All mutations can be scheduled next tick to avoid Bukkit reverting cancelled
 * events (common for some InventoryTypes).
 */
public final class ModuleClickHandler {

    private ModuleClickHandler() {
    }

    public static boolean handleOutput(
            Plugin plugin,
            InventoryClickEvent e,
            Player player,
            int outputRawSlot,
            Runnable craftOnce,
            Runnable craftShift,
            Runnable updateOutput) {
        if (e.getRawSlot() != outputRawSlot)
            return false;

        if (isBlockedOutputAction(e.getAction())) {
            e.setCancelled(true);
            return true;
        }

        e.setCancelled(true);

        boolean shift = isShiftClick(e);
        Runnable run = () -> {
            if (shift) {
                craftShift.run();
            } else {
                craftOnce.run();
            }
            updateOutput.run();
            player.updateInventory();
        };

        runNextTick(plugin, run);
        return true;
    }

    public static boolean handleShiftClickIntoInputs(
            Plugin plugin,
            InventoryClickEvent e,
            Player player,
            Inventory top,
            int[] inputSlots,
            ToIntFunction<ItemStack> preferredSlot,
            Runnable updateAfter) {

        if (!isShiftClick(e))
            return false;

        if (e.getClickedInventory() == null || e.getClickedInventory().equals(top))
            return false;

        if (top == null || inputSlots == null || inputSlots.length == 0)
            return false;

        ItemStack movingNow = e.getCurrentItem();
        if (movingNow == null || movingNow.getType().isAir())
            return false;

        // Decide now whether we handle it
        int preferred = preferredSlot == null ? -1 : preferredSlot.applyAsInt(movingNow);
        if (preferred < 0) {
            // Not for this module; let vanilla handle shift-click normally
            return false;
        }

        // From here on, we ARE handling it.
        ItemStack moving = movingNow.clone();
        Inventory clicked = e.getClickedInventory();
        int slot = e.getSlot();

        e.setCancelled(true);

        runNextTick(plugin, () -> {
            ItemStack remainder = insertIntoSlots(top, inputSlots, preferred, moving.clone());
            clicked.setItem(slot, (remainder == null || remainder.getAmount() <= 0) ? null : remainder);

            if (updateAfter != null)
                updateAfter.run();

            player.updateInventory();
        });

        return true;
    }

    public static boolean handleShiftClickOutOfInputs(
            Plugin plugin,
            InventoryClickEvent e,
            Player player,
            Inventory top,
            IntPredicate isInputSlot,
            Runnable updateAfter) {
        if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY)
            return false;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top))
            return false;

        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize())
            return false;
        if (isInputSlot != null && !isInputSlot.test(raw))
            return false;

        e.setCancelled(true);

        runNextTick(plugin, () -> {
            moveTopSlotToPlayer(top, raw, player);
            if (updateAfter != null)
                updateAfter.run();
            player.updateInventory();
        });

        return true;
    }

    public static void moveTopSlotToPlayer(Inventory top, int slot, Player player) {
        if (top == null || player == null)
            return;
        if (slot < 0 || slot >= top.getSize())
            return;

        ItemStack moving = top.getItem(slot);
        if (moving == null || moving.getType().isAir())
            return;

        var leftovers = player.getInventory().addItem(moving.clone());
        if (leftovers.isEmpty()) {
            top.setItem(slot, null);
        } else {
            top.setItem(slot, leftovers.values().iterator().next());
        }
    }

    public static ItemStack insertIntoSlots(Inventory inv, int[] slots, int preferredSlot, ItemStack stack) {
        if (inv == null || slots == null || slots.length == 0)
            return stack;
        if (stack == null || stack.getType().isAir())
            return stack;

        // Try preferred slot first (if valid), then all other allowed slots.
        int[] slotsToTry = orderedSlots(slots, preferredSlot);

        // Merge into existing stacks.
        for (int slot : slotsToTry) {
            if (slot < 0 || slot >= inv.getSize())
                continue;
            ItemStack cur = inv.getItem(slot);
            if (cur == null || cur.getType().isAir())
                continue;
            if (!cur.isSimilar(stack))
                continue;

            int max = cur.getMaxStackSize();
            int space = max - cur.getAmount();
            if (space <= 0)
                continue;

            int toMove = Math.min(space, stack.getAmount());
            ItemStack merged = cur.clone();
            merged.setAmount(cur.getAmount() + toMove);
            inv.setItem(slot, merged);

            stack.setAmount(stack.getAmount() - toMove);
            if (stack.getAmount() <= 0)
                return null;
        }

        // Empty slots.
        for (int slot : slotsToTry) {
            if (slot < 0 || slot >= inv.getSize())
                continue;
            ItemStack cur = inv.getItem(slot);
            if (cur != null && !cur.getType().isAir())
                continue;

            int toPlace = Math.min(stack.getMaxStackSize(), stack.getAmount());
            ItemStack placed = stack.clone();
            placed.setAmount(toPlace);
            inv.setItem(slot, placed);

            stack.setAmount(stack.getAmount() - toPlace);
            if (stack.getAmount() <= 0)
                return null;
        }

        return stack;
    }

    private static int[] orderedSlots(int[] slots, int preferred) {
        if (preferred < 0)
            return slots;
        boolean found = false;
        for (int s : slots) {
            if (s == preferred) {
                found = true;
                break;
            }
        }
        if (!found)
            return slots;

        int[] ordered = new int[slots.length];
        ordered[0] = preferred;
        int idx = 1;
        for (int s : slots) {
            if (s == preferred)
                continue;
            ordered[idx++] = s;
        }
        return ordered;
    }

    private static void runNextTick(Plugin plugin, Runnable r) {
        if (plugin != null) {
            Bukkit.getScheduler().runTask(plugin, r);
        } else {
            r.run();
        }
    }

    private static boolean isShiftClick(InventoryClickEvent e) {
        return e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || e.getClick() == ClickType.SHIFT_LEFT
                || e.getClick() == ClickType.SHIFT_RIGHT;
    }

    private static boolean isBlockedOutputAction(InventoryAction action) {
        return action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.SWAP_WITH_CURSOR
                || action == InventoryAction.HOTBAR_SWAP;
    }
}
