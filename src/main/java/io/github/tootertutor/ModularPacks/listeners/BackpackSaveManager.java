package io.github.tootertutor.ModularPacks.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;

/**
 * Manages debounced saving of backpack data to prevent excessive database
 * writes during rapid inventory changes.
 */
public final class BackpackSaveManager {

    private static final long SAVE_DEBOUNCE_TICKS = 10;
    private static final int SAVE_QUIET_TICKS = 8;

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;

    private final Map<UUID, Integer> lastStorageInteractionTick = new HashMap<>();
    private final Map<UUID, Integer> dirtySinceTick = new HashMap<>();
    private final Map<SaveKey, BukkitTask> pendingSaves = new HashMap<>();

    private record SaveKey(UUID playerId, UUID backpackId) {
    }

    public BackpackSaveManager(ModularPacksPlugin plugin, BackpackMenuRenderer renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
    }

    public void markInteraction(Player player, BackpackMenuHolder holder) {
        int now = Bukkit.getCurrentTick();
        lastStorageInteractionTick.put(player.getUniqueId(), now);
        dirtySinceTick.put(player.getUniqueId(), now);
        scheduleSave(player, holder);
    }

    public void scheduleSave(Player player, BackpackMenuHolder holder) {
        SaveKey key = new SaveKey(player.getUniqueId(), holder.backpackId());

        BukkitTask existing = pendingSaves.remove(key);
        if (existing != null)
            existing.cancel();

        UUID backpackId = holder.backpackId();

        pendingSaves.put(key, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSaves.remove(key);

            if (!player.isOnline())
                return;

            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof BackpackMenuHolder current))
                return;

            if (!current.backpackId().equals(backpackId))
                return;

            int now = Bukkit.getCurrentTick();

            if (!isSafeToPersist(player, now))
                return;

            renderer.saveVisibleStorageToData(current);
            plugin.repo().saveBackpack(current.data());
            plugin.sessions().refreshLinkedBackpacksThrottled(current.backpackId(), current.data());

            dirtySinceTick.remove(player.getUniqueId());
        }, SAVE_DEBOUNCE_TICKS));
    }

    public void flushSaveNow(Player player, BackpackMenuHolder holder) {
        flushSaveNow(player, holder, false);
    }

    public void flushSaveNow(Player player, BackpackMenuHolder holder, boolean force) {
        SaveKey key = new SaveKey(player.getUniqueId(), holder.backpackId());

        BukkitTask existing = pendingSaves.remove(key);
        if (existing != null)
            existing.cancel();

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof BackpackMenuHolder current
                && current.backpackId().equals(holder.backpackId())) {
            renderer.saveVisibleStorageToData(current);
            plugin.repo().saveBackpack(current.data());
            plugin.sessions().refreshLinkedBackpacksThrottled(current.backpackId(), current.data());
        } else {
            // fallback: save the provided holder's data directly
            plugin.repo().saveBackpack(holder.data());
            plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), holder.data());
        }

        dirtySinceTick.remove(player.getUniqueId());
    }

    public void cancelPendingSave(Player player, UUID backpackId) {
        SaveKey key = new SaveKey(player.getUniqueId(), backpackId);
        BukkitTask existing = pendingSaves.remove(key);
        if (existing != null)
            existing.cancel();
    }

    public void clearPlayerData(UUID playerId) {
        lastStorageInteractionTick.remove(playerId);
        dirtySinceTick.remove(playerId);
    }

    private boolean isSafeToPersist(Player player, int now) {
        if (player == null || !player.isOnline())
            return false;

        UUID playerId = player.getUniqueId();

        int last = lastStorageInteractionTick.getOrDefault(playerId, Integer.MIN_VALUE);
        if (last != Integer.MIN_VALUE && (now - last) <= SAVE_QUIET_TICKS)
            return false;

        return true;
    }
}
