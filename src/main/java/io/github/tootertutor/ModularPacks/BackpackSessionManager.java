package io.github.tootertutor.ModularPacks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.api.ModularPacksAPI;
import io.github.tootertutor.ModularPacks.api.modules.IModule;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Tracks active backpack "sessions" (open backpack GUI or a module GUI for that
 * backpack) and provides linked-backpack item refreshes.
 */
public final class BackpackSessionManager {

    private static final int LINKED_REFRESH_MIN_TICKS = 20; // 1s, throttled for engines

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    // backpackId -> viewerId
    private final Map<UUID, UUID> lockedToViewer = new HashMap<>();

    // backpackId -> last refresh tick
    private final Map<UUID, Integer> lastLinkedRefreshTick = new HashMap<>();

    public BackpackSessionManager(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
    }

    public UUID lockedTo(UUID backpackId) {
        return backpackId == null ? null : lockedToViewer.get(backpackId);
    }

    public String lockedToName(UUID backpackId) {
        UUID viewer = lockedTo(backpackId);
        if (viewer == null)
            return null;
        Player p = Bukkit.getPlayer(viewer);
        return p == null ? null : p.getName();
    }

    /**
     * Attempt to lock the backpack to this viewer. If {@code adminOverride} is
     * true and another viewer currently has it open, that viewer is closed and the
     * lock is taken.
     */
    public boolean tryLock(Player viewer, UUID backpackId, boolean adminOverride) {
        if (viewer == null || backpackId == null)
            return false;

        UUID viewerId = viewer.getUniqueId();
        UUID current = lockedToViewer.get(backpackId);

        if (current == null) {
            lockedToViewer.put(backpackId, viewerId);
            return true;
        }

        if (current.equals(viewerId))
            return true;

        // Stale lock: viewer no longer actually looking at this backpack.
        if (!isViewerStillInSession(current, backpackId)) {
            lockedToViewer.put(backpackId, viewerId);
            return true;
        }

        if (!adminOverride)
            return false;

        // Admin override: close the other viewer, and take the lock.
        Player other = Bukkit.getPlayer(current);
        if (other != null && other.isOnline()) {
            other.sendMessage(Text.c("&cAnother player has taken over the shared backpack."));
            other.closeInventory();
        }

        lockedToViewer.put(backpackId, viewerId);
        return true;
    }

    /**
     * Called when the viewer closes any inventory related to this backpack. Unlock
     * happens after a short delay so transitions (backpack -> module, module ->
     * backpack) don't drop the lock, and so a cancelled module-open can be
     * recovered by re-opening the backpack UI.
     */
    public void onRelatedInventoryClose(Player viewer, UUID backpackId) {
        if (viewer == null || backpackId == null)
            return;

        UUID viewerId = viewer.getUniqueId();
        UUID current = lockedToViewer.get(backpackId);
        if (current == null || !current.equals(viewerId))
            return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Only unlock if they didn't transition into another session screen.
            if (isViewerStillInSession(viewerId, backpackId))
                return;

            UUID cur2 = lockedToViewer.get(backpackId);
            if (cur2 != null && cur2.equals(viewerId)) {
                lockedToViewer.remove(backpackId);
            }
        }, 2L);
    }

    public void releaseAllFor(UUID viewerId) {
        if (viewerId == null)
            return;

        lockedToViewer.entrySet().removeIf(e -> viewerId.equals(e.getValue()));
    }

    /**
     * Release the lock for a specific backpack ID, regardless of who holds it.
     */
    public void releaseLock(UUID backpackId) {
        if (backpackId == null)
            return;
        lockedToViewer.remove(backpackId);
    }

    /**
     * Refresh all online players' backpack items that point at this backpackId.
     * Throttled to avoid scanning inventories too frequently (magnet/feeding).
     */
    public void refreshLinkedBackpacksThrottled(UUID backpackId, BackpackData data) {
        if (backpackId == null || data == null)
            return;

        int now = Bukkit.getCurrentTick();
        int last = lastLinkedRefreshTick.getOrDefault(backpackId, -999999);
        if (now - last < LINKED_REFRESH_MIN_TICKS)
            return;
        lastLinkedRefreshTick.put(backpackId, now);

        BackpackTypeDef type = plugin.cfg().findType(data.backpackType());
        if (type == null)
            return;

        int totalSlots = type.rows() * 9;
        Keys keys = plugin.keys();

        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = p.getInventory().getContents();
            if (contents != null) {
                for (int i = 0; i < contents.length; i++) {
                    ItemStack it = contents[i];
                    if (!isLinkedBackpack(keys, it, backpackId))
                        continue;
                    if (backpackItems.refreshInPlace(it, type, backpackId, data, totalSlots)) {
                        p.getInventory().setItem(i, it);
                    }
                }
            }

            ItemStack cursor = p.getItemOnCursor();
            if (isLinkedBackpack(keys, cursor, backpackId)) {
                if (backpackItems.refreshInPlace(cursor, type, backpackId, data, totalSlots)) {
                    p.setItemOnCursor(cursor);
                }
            }
        }
    }

    private boolean isViewerStillInSession(UUID viewerId, UUID backpackId) {
        Player p = Bukkit.getPlayer(viewerId);
        if (p == null || !p.isOnline())
            return false;

        var view = p.getOpenInventory();
        if (view == null)
            return false;

        var top = view.getTopInventory();
        if (top == null)
            return false;

        var holder = top.getHolder();
        if (holder instanceof BackpackMenuHolder bmh) {
            return backpackId.equals(bmh.backpackId());
        }
        if (holder instanceof ModuleScreenHolder msh) {
            return backpackId.equals(msh.backpackId());
        }

        // Check all registered modules for active sessions
        for (IModule module : ModularPacksAPI.getInstance().getModuleRegistry().getAllModules()) {
            UUID moduleBackpackId = module.getSessionBackpackId(p);
            if (moduleBackpackId != null && moduleBackpackId.equals(backpackId)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isLinkedBackpack(Keys keys, ItemStack it, UUID backpackId) {
        if (keys == null || backpackId == null)
            return false;
        if (ItemStacks.isAir(it) || !it.hasItemMeta())
            return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null)
            return false;
        String idStr = meta.getPersistentDataContainer().get(keys.BACKPACK_ID, PersistentDataType.STRING);
        if (idStr == null)
            return false;
        try {
            return backpackId.equals(UUID.fromString(idStr));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
