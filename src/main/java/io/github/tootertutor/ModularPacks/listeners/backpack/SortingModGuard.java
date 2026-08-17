package io.github.tootertutor.ModularPacks.listeners.backpack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

/**
 * Best-effort guard against client-side sorting mods that spam inventory clicks
 * within the same tick/frame, which can leave items "in-flight" on the cursor
 * and effectively delete them from the persisted snapshot.
 */
public final class SortingModGuard {

    private static final int SORT_MOD_CANCEL_WINDOW_TICKS = 8;
    private static final int SORT_MOD_PER_TICK_THRESHOLD = 4;
    private static final int SORT_MOD_WINDOW_TICKS = 8;
    private static final int SORT_MOD_WINDOW_THRESHOLD = 14;

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;
    private final BackpackInventoryService inventoryService;

    private final Map<UUID, Integer> sortBurstTick = new HashMap<>();
    private final Map<UUID, Integer> sortBurstCount = new HashMap<>();
    private final Map<UUID, Integer> cancelClicksUntilTick = new HashMap<>();
    private final Map<UUID, ArrayDeque<Integer>> sortWindowTicks = new HashMap<>();
    private final Map<UUID, Integer> sortBurstNotifiedAtTick = new HashMap<>();

    public SortingModGuard(ModularPacksPlugin plugin, BackpackMenuRenderer renderer,
            BackpackInventoryService inventoryService) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.inventoryService = inventoryService;
    }

    public boolean isSortingModBurst(Player player, int now) {
        UUID playerId = player.getUniqueId();

        int until = cancelClicksUntilTick.getOrDefault(playerId, -1);
        if (now <= until) {
            notifyBurstOnce(player, now);
            return true;
        }

        int lastTick = sortBurstTick.getOrDefault(playerId, Integer.MIN_VALUE);
        int count = (lastTick == now) ? (sortBurstCount.getOrDefault(playerId, 0) + 1) : 1;

        sortBurstTick.put(playerId, now);
        sortBurstCount.put(playerId, count);

        ArrayDeque<Integer> window = sortWindowTicks.computeIfAbsent(playerId, _k -> new ArrayDeque<>());
        window.addLast(now);
        while (!window.isEmpty() && (now - window.peekFirst()) > SORT_MOD_WINDOW_TICKS) {
            window.pollFirst();
        }

        if (count >= SORT_MOD_PER_TICK_THRESHOLD || window.size() >= SORT_MOD_WINDOW_THRESHOLD) {
            cancelClicksUntilTick.put(playerId, now + SORT_MOD_CANCEL_WINDOW_TICKS);
            notifyBurstOnce(player, now);
            return true;
        }

        return false;
    }

    public boolean isCancelWindow(UUID playerId, int now) {
        int until = cancelClicksUntilTick.getOrDefault(playerId, -1);
        return now <= until;
    }

    public void stabilizeAfterBurst(Player player, BackpackMenuHolder holder) {
        if (player == null || holder == null)
            return;

        boolean changed = stashCursorIntoBackpackOrPlayer(player, holder);
        if (changed) {
            plugin.repo().saveBackpack(holder.data());
        }

        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    public void clearPlayerData(UUID playerId) {
        sortBurstTick.remove(playerId);
        sortBurstCount.remove(playerId);
        cancelClicksUntilTick.remove(playerId);
        sortWindowTicks.remove(playerId);
        sortBurstNotifiedAtTick.remove(playerId);
    }

    private void notifyBurstOnce(Player player, int now) {
        UUID playerId = player.getUniqueId();
        int last = sortBurstNotifiedAtTick.getOrDefault(playerId, Integer.MIN_VALUE);
        if (last == now)
            return;

        sortBurstNotifiedAtTick.put(playerId, now);
    }

    private boolean stashCursorIntoBackpackOrPlayer(Player player, BackpackMenuHolder holder) {
        ItemStack cursor = player.getItemOnCursor();
        if (ItemStacks.isAir(cursor))
            return false;

        if (holder.getInventory() == null) {
            return stashCursorIntoPlayer(player);
        }

        renderer.saveVisibleStorageToData(holder);
        ItemStack remaining = inventoryService.insertIntoBackpackLogical(holder, cursor.clone());
        player.setItemOnCursor(null);
        if (ItemStacks.isNotAir(remaining)) {
            var leftovers = player.getInventory().addItem(remaining);
            for (ItemStack left : leftovers.values()) {
                if (ItemStacks.isNotAir(left))
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        renderer.render(holder);
        return true;
    }

    private boolean stashCursorIntoPlayer(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (ItemStacks.isAir(cursor))
            return false;

        player.setItemOnCursor(null);
        var leftovers = player.getInventory().addItem(cursor);
        for (ItemStack left : leftovers.values()) {
            if (ItemStacks.isNotAir(left))
                player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        return true;
    }
}
