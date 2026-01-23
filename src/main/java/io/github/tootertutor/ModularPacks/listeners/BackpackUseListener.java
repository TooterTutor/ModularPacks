package io.github.tootertutor.ModularPacks.listeners;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.item.Keys;

public final class BackpackUseListener implements Listener {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;

    public BackpackUseListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BackpackMenuRenderer(plugin);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK)
            return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta())
            return;

        Keys keys = plugin.keys();
        var pdc = item.getItemMeta().getPersistentDataContainer();

        String idStr = pdc.get(keys.BACKPACK_ID, PersistentDataType.STRING);
        String typeId = pdc.get(keys.BACKPACK_TYPE, PersistentDataType.STRING);
        if (idStr == null || typeId == null)
            return;

        UUID backpackId;
        try {
            backpackId = UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            return;
        }

        e.setCancelled(true);
        plugin.repo().ensureBackpackExists(backpackId, typeId, p.getUniqueId(), p.getName());

        // Load backpack data early to check share validity
        var data = plugin.repo().loadOrCreate(backpackId, typeId);

        // Auto-detach if host stopped sharing
        if (data != null && data.shareHostId() != null) {
            UUID hostId = data.shareHostId();
            String hostType = plugin.repo().findBackpackType(hostId);
            boolean hostStillShared = false;
            if (hostType != null) {
                var hostData = plugin.repo().loadOrCreate(hostId, hostType);
                hostStillShared = hostData != null && hostData.isShared() && hostData.isShareHost();
            }

            if (!hostStillShared) {
                // Restore joiner's own contents and detach from host
                var restored = plugin.repo().loadJoinerContents(backpackId);
                if (restored != null && restored.contentsBytes() != null) {
                    data.contentsBytes(restored.contentsBytes());
                    data.installedModules().clear();
                    data.installedSnapshots().clear();
                    data.moduleStates().clear();
                    if (restored.installedModules() != null) {
                        data.installedModules().putAll(restored.installedModules());
                    }
                    if (restored.installedSnapshots() != null) {
                        data.installedSnapshots().putAll(restored.installedSnapshots());
                    }
                    if (restored.moduleStates() != null) {
                        data.moduleStates().putAll(restored.moduleStates());
                    }
                }

                data.setShared(false);
                data.sharePassword("");
                data.shareHostId(null);
                plugin.repo().saveBackpack(data);
            }
        }

        // Attempt normal lock first
        boolean locked = plugin.sessions().tryLock(p, backpackId, false);

        // If locked by someone else, allow share members to take over the lock
        if (!locked) {
            if (data != null && data.isShared()) {
                locked = plugin.sessions().tryLock(p, backpackId, true);
                if (locked) {
                    p.sendMessage("You have taken over the shared backpack session.");
                }
            }
        }

        if (!locked) {
            String lockedTo = plugin.sessions().lockedToName(backpackId);
            if (lockedTo == null)
                lockedTo = "someone else";
            p.sendMessage("That backpack is currently open by " + lockedTo + ".");
            return;
        }

        if (renderer.openMenu(p, backpackId, typeId) == null) {
            // If the GUI can't open (missing type config), don't leave a stale lock behind.
            plugin.sessions().onRelatedInventoryClose(p, backpackId);
        }
    }
}
