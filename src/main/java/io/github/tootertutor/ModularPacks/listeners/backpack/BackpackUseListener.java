package io.github.tootertutor.ModularPacks.listeners.backpack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.events.backpack.BackpackOpenCause;
import io.github.tootertutor.ModularPacks.api.events.backpack.BackpackOpenEvent;
import io.github.tootertutor.ModularPacks.api.events.backpack.BackpackOpenedEvent;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class BackpackUseListener implements Listener {

    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;

    private record OutsideClick(InventoryView view, long time, ItemStack backpack) {
    }

    private final Map<UUID, OutsideClick> outsideClicks = new HashMap<>();
    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;
    private final BackpackItems backpackItems;

    public BackpackUseListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BackpackMenuRenderer(plugin);
        this.backpackItems = new BackpackItems(plugin);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        EquipmentSlot hand = e.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)
            return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK)
            return;
        Player p = e.getPlayer();
        var priority = BackpackInteractionRouter.resolve(plugin, e);
        if (priority == BackpackInteractionPriority.Action.PLACED_BACKPACK) {
            if (backpackItems.isBackpack(e.getItem())) {
                e.setUseItemInHand(Result.DENY);
            }
            return;
        }
        if (priority == BackpackInteractionPriority.Action.PLACE_HELD) {
            return;
        }
        if (priority == BackpackInteractionPriority.Action.VANILLA
                || priority == BackpackInteractionPriority.Action.NONE) {
            if (backpackItems.isBackpack(e.getItem())) {
                e.setUseItemInHand(Result.DENY);
            }
            return;
        }

        ItemStack item = e.getItem();

        if (item == null || !item.hasItemMeta())
            return;

        ItemStack backpackItem = hand == EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
        if (backpackItems.ensureWearableTag(backpackItem)) {
            if (hand == EquipmentSlot.OFF_HAND) {
                p.getInventory().setItemInOffHand(backpackItem);
            } else {
                p.getInventory().setItemInMainHand(backpackItem);
            }
        }

        openBackpackFromItem(p, () -> e.setCancelled(true), item, BackpackOpenCause.ITEM_USE);
    }

    private ItemStack equippedBackpack(Player player) {
        ItemStack item = plugin.modelManager().equippedBackpack(player);
        return backpackItems.isBackpack(item) ? item : player.getInventory().getChestplate();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEquippedHotkey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack equipped = equippedBackpack(player);
        if (!backpackItems.isBackpack(equipped)) {
            return;
        }
        // Cancel the swap before custom open events or menu/session processing.
        openBackpackFromItem(player, () -> event.setCancelled(true), equipped,
                BackpackOpenCause.EQUIPPED_HOTKEY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOutsideClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        OutsideClick previous = outsideClicks.remove(id);
        // Only the player's ordinary inventory: never hijack containers or plugin
        // menus.
        if (event.isCancelled() || event.getView().getType() != InventoryType.CRAFTING
                || !BackpackInventoryGesture.isOutsideLeftClick(event.getRawSlot(), event.getClick())
                || !ItemStacks.isAir(event.getCursor())) {
            return;
        }
        ItemStack equipped = equippedBackpack(player);
        if (!backpackItems.isBackpack(equipped)) {
            return;
        }
        long now = System.nanoTime();
        if (previous == null || previous.view() != event.getView()
                || now - previous.time() > DOUBLE_CLICK_NANOS
                || !previous.backpack().equals(equipped)) {
            outsideClicks.put(id, new OutsideClick(event.getView(), now, equipped.clone()));
            return;
        }
        event.setCancelled(true);
        InventoryView view = event.getView();
        ItemStack expected = equipped.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory() != view
                    || !ItemStacks.isAir(player.getItemOnCursor())
                    || !expected.equals(equippedBackpack(player))) {
                return;
            }
            openBackpackFromItem(player, () -> {
            }, expected, BackpackOpenCause.INVENTORY_GESTURE);
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        outsideClicks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        outsideClicks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        outsideClicks.remove(event.getWhoClicked().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        outsideClicks.remove(event.getPlayer().getUniqueId());
    }

    private void openBackpackFromItem(Player player, Runnable cancelInteraction, ItemStack item,
            BackpackOpenCause cause) {
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

        cancelInteraction.run();

        BackpackOpenEvent openEvent = new BackpackOpenEvent(player, backpackId, typeId, cause, null);
        plugin.getServer().getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) {
            return;
        }

        plugin.repo().ensureBackpackExists(backpackId, typeId, player.getUniqueId(), player.getName());

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
                if (restored != null) {
                    plugin.backpackStorage().copyEncodedContents(restored, data);
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

        // Attempt lock with takeover for shared backpacks (always force if joined)
        boolean locked;
        if (data != null && data.isShared()) {
            // Shared backpack: always allow takeover to ensure exclusive view
            locked = plugin.sessions().tryLock(player, backpackId, true);
            if (locked && !data.isShareHost()) {
                // We're a joiner taking over
                player.sendMessage("You have taken over the shared backpack session.");
            }
        } else {
            // Normal lock first
            locked = plugin.sessions().tryLock(player, backpackId, false);
        }

        if (!locked) {
            String lockedTo = plugin.sessions().lockedToName(backpackId);
            if (lockedTo == null)
                lockedTo = "someone else";
            player.sendMessage("That backpack is currently open by " + lockedTo + ".");
            return;
        }

        if (renderer.openMenu(player, backpackId, typeId) == null) {
            // If the GUI can't open (missing type config), don't leave a stale lock behind.
            plugin.sessions().onRelatedInventoryClose(player, backpackId);
            return;
        }

        plugin.getServer().getPluginManager()
                .callEvent(new BackpackOpenedEvent(player, backpackId, typeId, cause, null));
    }
}
