package io.github.tootertutor.ModularPacks.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;
import io.github.tootertutor.ModularPacks.gui.ScreenRouter;
import io.github.tootertutor.ModularPacks.gui.SlotLayout;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.modules.FurnaceStateCodec;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Main event listener for backpack inventory interactions.
 * Delegates complex logic to specialized service classes:
 * - BackpackSaveManager: Save debouncing
 * - SortingModGuard: Sorting mod detection
 * - BackpackInventoryService: Inventory operations
 * - ModuleSocketHandler: Module interactions
 */
public final class BackpackMenuListener implements Listener {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;

    // Service instances
    private final BackpackSaveManager saveManager;
    private final SortingModGuard sortGuard;
    private final BackpackInventoryService inventoryService;
    private final ModuleSocketHandler moduleHandler;
    private final BackpackSharingHost sharingHost;
    private final BackpackSharingJoiner sharingJoiner;

    // UI-specific state (page navigation)
    private final Map<UUID, Integer> ignoreCloseUntilTick = new HashMap<>();

    public BackpackMenuListener(ModularPacksPlugin plugin) {
        this(plugin, new BackpackMenuRenderer(plugin), new ScreenRouter(plugin));
    }

    public BackpackMenuListener(ModularPacksPlugin plugin, BackpackMenuRenderer renderer, ScreenRouter screens) {
        this.plugin = plugin;
        this.renderer = renderer;

        // Initialize service classes
        this.saveManager = new BackpackSaveManager(plugin, renderer);
        this.sortGuard = new SortingModGuard(plugin);
        this.inventoryService = new BackpackInventoryService(plugin);
        this.moduleHandler = new ModuleSocketHandler(plugin, renderer, screens, saveManager);
        this.sharingHost = new BackpackSharingHost(plugin, renderer, saveManager);
        this.sharingJoiner = new BackpackSharingJoiner(plugin, renderer, saveManager);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;

        var topHolder = e.getView().getTopInventory().getHolder();

        if (!(topHolder instanceof BackpackMenuHolder holder))
            return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();

        if (e.getClickedInventory() == null)
            return;

        int now = Bukkit.getCurrentTick();

        boolean clickedTop = e.getClickedInventory().equals(top);
        int rawSlot = e.getRawSlot();

        boolean hasNavRow = holder.paginated() || holder.type().upgradeSlots() > 0;
        int visibleStorage = SlotLayout.storageAreaSize(topSize, hasNavRow);

        // Hard block: never allow swapping a backpack item into an open backpack
        if (isBackpackHotbarSwap(player, e) || isBackpack(e.getCursor()) || isBackpack(e.getCurrentItem())) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            return;
        }

        // Respect container rules (AllowShulkerBoxes / AllowBundles)
        if (clickedTop && rawSlot >= 0 && rawSlot < visibleStorage) {
            InventoryAction action = e.getAction();
            if (action == InventoryAction.PLACE_ALL
                    || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME
                    || action == InventoryAction.SWAP_WITH_CURSOR) {
                ItemStack cursor = e.getCursor();
                if (ItemStacks.isNotAir(cursor) && !plugin.cfg().isAllowedInBackpack(cursor)) {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return;
                }
            }

            if (action == InventoryAction.HOTBAR_SWAP) {
                int btn = e.getHotbarButton();
                if (btn >= 0 && btn <= 8) {
                    ItemStack hotbar = player.getInventory().getItem(btn);
                    if (ItemStacks.isNotAir(hotbar) && !plugin.cfg().isAllowedInBackpack(hotbar)) {
                        e.setCancelled(true);
                        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                        return;
                    }
                }
            }
        }

        // Detect sorting mod bursts
        boolean relevantToBackpack = clickedTop || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || e.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || e.getAction() == InventoryAction.HOTBAR_SWAP;

        if (relevantToBackpack && sortGuard.isSortingModBurst(player, now)) {
            e.setCancelled(true);
            sortGuard.stabilizeAfterBurst(player, holder);
            return;
        }

        if (clickedTop && rawSlot >= 0 && rawSlot < visibleStorage && e.getAction() != InventoryAction.NOTHING) {
            saveManager.markInteraction(player, holder);
        }

        if (!clickedTop && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            saveManager.markInteraction(player, holder);
        }

        // Protect invalid slots on the last page
        if (clickedTop && holder.paginated()) {
            int raw = e.getRawSlot();

            if (raw >= 0 && raw < visibleStorage) {
                int remaining = holder.logicalSlots() - holder.page() * 45;
                int valid = Math.max(0, Math.min(45, remaining));
                valid = Math.min(valid, visibleStorage);

                if (raw >= valid) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        boolean shiftRightSortToggle = clickedTop
                && e.getClick() == ClickType.SHIFT_RIGHT
                && rawSlot == SlotLayout.sortButtonSlot(topSize, holder.upgradeSlots(), holder.paginated());

        // Shift-click handling
        if (!shiftRightSortToggle && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {

            // Shift-click from top -> player
            if (clickedTop) {
                // Route socket clicks to module handler
                if (holder.upgradeSlots().contains(rawSlot)) {
                    moduleHandler.handleUpgradeSocketClick(e, player, holder, rawSlot);
                    return;
                }
                if (rawSlot >= visibleStorage) {
                    e.setCancelled(true);
                }
                return;
            }

            // Shift-click from player -> top: insert into logical storage
            e.setCancelled(true);

            ItemStack moving = e.getCurrentItem();
            if (ItemStacks.isAir(moving))
                return;
            if (isBackpack(moving)) {
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
            if (!plugin.cfg().isAllowedInBackpack(moving)) {
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            renderer.saveVisibleStorageToData(holder);

            ItemStack remainder = inventoryService.insertIntoBackpackLogical(holder, moving.clone());

            if (remainder == null || remainder.getAmount() <= 0) {
                e.setCurrentItem(null);
            } else {
                e.setCurrentItem(remainder);
            }

            renderer.render(holder);
            saveManager.markInteraction(player, holder);
            return;
        }

        // Protect nav row (except upgrade sockets)
        if (clickedTop && hasNavRow && rawSlot >= visibleStorage) {

            if (holder.upgradeSlots().contains(rawSlot)) {
                moduleHandler.handleUpgradeSocketClick(e, player, holder, rawSlot);
                return;
            }

            int sortSlot = SlotLayout.sortButtonSlot(topSize, holder.upgradeSlots(), holder.paginated());
            if (sortSlot >= 0 && rawSlot == sortSlot) {
                e.setCancelled(true);
                ItemStack cursor = player.getItemOnCursor();
                if (ItemStacks.isNotAir(cursor)) {
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return;
                }

                boolean shiftRightClick = e.getClick() == ClickType.SHIFT_RIGHT;
                if (shiftRightClick) {
                    boolean locked = holder.toggleSortLocked();
                    player.sendMessage(Text.c(locked ? "&eSort locked." : "&aSort unlocked."));
                    renderer.render(holder);
                    return;
                }

                if (e.getClick().isRightClick() && !e.getClick().isShiftClick()) {
                    if (holder.sortLocked()) {
                        player.sendMessage(Text.c("&cSort is locked. Shift + right-click to unlock."));
                        return;
                    }

                    holder.sortMode(holder.sortMode().next());
                    renderer.render(holder);
                    saveManager.scheduleSave(player, holder);
                    return;
                }

                if (holder.sortLocked()) {
                    player.sendMessage(Text.c("&cSort is locked. Shift + right-click to unlock."));
                    return;
                }

                inventoryService.sortBackpack(holder, renderer);
                renderer.render(holder);
                saveManager.scheduleSave(player, holder);
                return;
            }

            // Mode button (share/private toggle)
            int modeSlot = SlotLayout.modeButtonSlot(topSize, holder.upgradeSlots(), holder.paginated(), sortSlot);
            if (modeSlot >= 0 && rawSlot == modeSlot) {
                e.setCancelled(true);
                ItemStack cursor = player.getItemOnCursor();
                if (ItemStacks.isNotAir(cursor)) {
                    var leftover = player.getInventory().addItem(cursor);
                    player.setItemOnCursor(null);
                    if (!leftover.isEmpty()) {
                        leftover.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
                    }
                }

                boolean isLeftClick = e.getClick().isLeftClick();
                boolean isRightClick = e.getClick().isRightClick();

                if (!holder.data().isShared()) {
                    // Private mode: Left-click → Host Mode (no password required), Right-click →
                    // Join Mode
                    if (isLeftClick) {
                        sharingHost.hostBackpack(player, holder);
                    } else if (isRightClick) {
                        // Open join dialog
                        sharingJoiner.openJoinDialog(player, holder);
                    }
                } else {
                    // Shared mode: Left-click → Private, Right-click → Set Password (host) or
                    // Change Join (joiner)
                    if (isLeftClick) {
                        // Try to leave (if joiner) or disable share (if host)
                        if (holder.data().isShareHost()) {
                            sharingHost.stopHosting(player, holder);
                        } else {
                            sharingJoiner.leaveJoinedBackpack(player, holder);
                            player.sendMessage(Text.c("&aLeft shared backpack."));
                        }
                    } else if (isRightClick) {
                        if (holder.data().isShareHost()) {
                            // Host mode: Set/change password
                            sharingHost.openPasswordDialog(player, holder);
                        } else {
                            // Join mode: Change join (re-open join dialog)
                            sharingJoiner.openJoinDialog(player, holder);
                        }
                    }
                }
                return;
            }

            if (holder.paginated()) {
                int prevSlot = SlotLayout.prevButtonSlot(topSize);
                int nextSlot = SlotLayout.nextButtonSlot(topSize);

                if (rawSlot == prevSlot || rawSlot == nextSlot) {
                    e.setCancelled(true);
                    ItemStack cursor = player.getItemOnCursor();
                    if (ItemStacks.isNotAir(cursor)) {
                        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                        return;
                    }

                    int direction = (rawSlot == prevSlot) ? -1 : 1;
                    changePage(player, holder, holder.page() + direction);
                    return;
                }
            }

            e.setCancelled(true);
            return;
        }

        // Schedule debounced save for normal storage moves
        if (clickedTop && rawSlot >= 0 && rawSlot < visibleStorage) {
            if (e.getAction() != InventoryAction.NOTHING) {
                saveManager.markInteraction(player, holder);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;

        var topHolder = e.getView().getTopInventory().getHolder();

        if (!(topHolder instanceof BackpackMenuHolder holder))
            return;

        int now = Bukkit.getCurrentTick();

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();

        boolean hasNavRow = holder.paginated() || holder.type().upgradeSlots() > 0;
        int visibleStorage = SlotLayout.storageAreaSize(topSize, hasNavRow);

        ItemStack cursor = e.getOldCursor();
        if (ItemStacks.isNotAir(cursor) && !plugin.cfg().isAllowedInBackpack(cursor)) {
            for (int rawSlot : e.getRawSlots()) {
                if (rawSlot >= 0 && rawSlot < visibleStorage) {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return;
                }
            }
        }

        boolean targetsTop = false;
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                targetsTop = true;
                break;
            }
        }
        if (targetsTop && sortGuard.isSortingModBurst(player, now)) {
            e.setCancelled(true);
            sortGuard.stabilizeAfterBurst(player, holder);
            return;
        }

        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < topSize && hasNavRow && rawSlot >= visibleStorage) {
                e.setCancelled(true);
                return;
            } else if (rawSlot >= 0 && rawSlot < topSize) {
                saveManager.markInteraction(player, holder);
                break;
            }
        }

        // Cancel drags that target invalid slots on the last page
        if (holder.paginated()) {
            int remaining = holder.logicalSlots() - holder.page() * 45;
            int valid = Math.max(0, Math.min(45, remaining));
            valid = Math.min(valid, visibleStorage);

            for (int rawSlot : e.getRawSlots()) {
                if (rawSlot < topSize && rawSlot >= 0 && rawSlot < visibleStorage && rawSlot >= valid) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player))
            return;
        if (!(e.getView().getTopInventory().getHolder() instanceof BackpackMenuHolder holder))
            return;

        int now = Bukkit.getCurrentTick();
        int ignoreUntil = ignoreCloseUntilTick.getOrDefault(player.getUniqueId(), -1);
        if (now > ignoreUntil) {
            try {
                player.playSound(player.getLocation(), plugin.cfg().backpackOpenSound(), 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }

        // Eject disallowed items on open
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof BackpackMenuHolder openHolder))
                return;
            if (!openHolder.backpackId().equals(holder.backpackId()))
                return;

            EjectResult moved = ejectProhibitedFromData(player, openHolder);
            if (moved.backpacks > 0 || moved.blocked > 0) {
                renderer.render(openHolder);
                saveManager.scheduleSave(player, openHolder);
                if (moved.backpacks > 0) {
                    player.sendMessage(Text.c("&cBackpacks can't be stored inside backpacks. Moved " + moved.backpacks
                            + " backpack(s) back to you."));
                }
                if (moved.blocked > 0) {
                    player.sendMessage(Text.c("&cSome items are blocked from backpacks by server config. Moved "
                            + moved.blocked + " item(s) back to you."));
                }
                player.updateInventory();
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player))
            return;

        // MODULE SCREEN CLOSE
        if (e.getInventory().getHolder() instanceof ModuleScreenHolder msh) {
            BackpackData data = plugin.repo().loadOrCreate(msh.backpackId(), msh.backpackType());
            Inventory inv = e.getInventory();

            // Furnace-like: save as FurnaceStateCodec
            if (msh.screenType() == ScreenType.SMELTING
                    || msh.screenType() == ScreenType.BLASTING
                    || msh.screenType() == ScreenType.SMOKING) {

                byte[] existing = data.moduleStates().get(msh.moduleId());
                FurnaceStateCodec.State old = FurnaceStateCodec.decode(existing);

                FurnaceStateCodec.State fs = new FurnaceStateCodec.State();
                fs.input = inv.getItem(0);
                fs.fuel = inv.getItem(1);
                fs.output = inv.getItem(2);

                if (old != null) {
                    fs.burnTime = old.burnTime;
                    fs.burnTotal = old.burnTotal;
                    fs.cookTime = old.cookTime;
                    fs.cookTotal = old.cookTotal;
                    fs.xpStored = old.xpStored;
                }

                data.moduleStates().put(msh.moduleId(), FurnaceStateCodec.encode(fs));
                plugin.repo().saveBackpack(data);
                plugin.sessions().refreshLinkedBackpacksThrottled(msh.backpackId(), data);
                plugin.sessions().onRelatedInventoryClose(player, msh.backpackId());
                return;
            }

            // Everything else: save as ItemStackCodec
            ItemStack[] items = new ItemStack[inv.getSize()];
            for (int i = 0; i < items.length; i++) {
                items[i] = inv.getItem(i);
            }

            // Clear derived output slots
            switch (msh.screenType()) {
                case CRAFTING -> {
                    if (items.length > 0)
                        items[0] = null;
                }
                case STONECUTTER -> {
                    if (items.length > 1)
                        items[1] = null;
                }
                case SMITHING -> {
                    if (items.length > 3)
                        items[3] = null;
                }
                case ANVIL -> {
                    if (items.length > 2)
                        items[2] = null;
                }
                default -> {
                }
            }

            // Restock module state merging
            if ((msh.screenType() == ScreenType.HOPPER || msh.screenType() == ScreenType.DROPPER)
                    && isRestockModule(msh, data)) {
                byte[] merged = mergeRestockState(data, msh.moduleId(), msh.screenType(), items);
                items = ItemStackCodec.fromBytes(merged);

                byte[] snap = data.installedSnapshots().get(msh.moduleId());
                if (snap != null && snap.length > 0) {
                    try {
                        ItemStack[] snapArr = ItemStackCodec.fromBytes(snap);
                        if (snapArr.length > 0 && snapArr[0] != null) {
                            ItemMeta meta = snapArr[0].getItemMeta();
                            if (meta != null) {
                                String b64 = java.util.Base64.getEncoder().encodeToString(merged);
                                meta.getPersistentDataContainer().set(plugin.keys().MODULE_STATE_B64,
                                        PersistentDataType.STRING, b64);
                                snapArr[0].setItemMeta(meta);
                                data.installedSnapshots().put(msh.moduleId(),
                                        ItemStackCodec.toBytes(snapArr));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            data.moduleStates().put(msh.moduleId(), ItemStackCodec.toBytes(items));
            plugin.repo().saveBackpack(data);
            plugin.sessions().refreshLinkedBackpacksThrottled(msh.backpackId(), data);
            plugin.sessions().onRelatedInventoryClose(player, msh.backpackId());
            return;
        }

        // BACKPACK CLOSE
        if (e.getInventory().getHolder() instanceof BackpackMenuHolder holder) {
            int now = Bukkit.getCurrentTick();
            int ignoreUntil = ignoreCloseUntilTick.getOrDefault(player.getUniqueId(), -1);

            if (now <= ignoreUntil) {
                return;
            }

            saveManager.cancelPendingSave(player, holder.backpackId());

            renderer.saveVisibleStorageToData(holder);

            EjectResult moved = ejectProhibitedFromData(player, holder);
            if (moved.backpacks > 0) {
                player.sendMessage(Text.c("&cBackpacks can't be stored inside backpacks. Moved " + moved.backpacks
                        + " backpack(s) back to you."));
            }
            if (moved.blocked > 0) {
                player.sendMessage(Text.c("&cSome items are blocked from backpacks by server config. Moved "
                        + moved.blocked + " item(s) back to you."));
            }

            plugin.repo().saveBackpack(holder.data());
            moduleHandler.refreshBackpackItemsFor(player, holder);
            plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), holder.data());
            plugin.sessions().onRelatedInventoryClose(player, holder.backpackId());

            try {
                player.playSound(player.getLocation(), plugin.cfg().backpackCloseSound(), 1.0f, 1.0f);
            } catch (Exception ignored) {
            }

            sortGuard.clearPlayerData(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (player == null)
            return;
        UUID playerId = player.getUniqueId();
        plugin.sessions().releaseAllFor(playerId);
        saveManager.clearPlayerData(playerId);
        sortGuard.clearPlayerData(playerId);
    }

    private void changePage(Player player, BackpackMenuHolder holder, int newPage) {
        renderer.saveVisibleStorageToData(holder);

        int clamped = Math.max(0, Math.min(newPage, pageCount(holder) - 1));
        if (clamped == holder.page())
            return;

        holder.page(clamped);

        saveManager.scheduleSave(player, holder);

        if (!plugin.cfg().resizeGui()) {
            renderer.render(holder);
            return;
        }

        // Reopen next tick (resize GUI mode)
        int now = Bukkit.getCurrentTick();
        ignoreCloseUntilTick.put(player.getUniqueId(), now + 1);

        Bukkit.getScheduler().runTask(plugin, () -> {
            renderer.openMenu(player, holder.data(), holder.type(), holder.page());
        });
    }

    private int pageCount(BackpackMenuHolder holder) {
        if (!holder.paginated())
            return 1;
        int logical = holder.logicalSlots();
        return Math.max(1, (int) Math.ceil(logical / 45.0));
    }

    private EjectResult ejectProhibitedFromData(Player player, BackpackMenuHolder holder) {
        if (player == null || holder == null)
            return new EjectResult(0, 0);

        int movedBackpacks = 0;
        int movedBlocked = 0;

        ItemStack[] logical = ItemStackCodec.fromBytes(holder.data().contentsBytes());
        int logicalSize = holder.logicalSlots();
        if (logical.length != logicalSize) {
            ItemStack[] resized = new ItemStack[logicalSize];
            System.arraycopy(logical, 0, resized, 0, Math.min(logical.length, logicalSize));
            logical = resized;
        }

        for (int i = 0; i < logical.length; i++) {
            ItemStack it = logical[i];
            if (ItemStacks.isAir(it))
                continue;

            if (isBackpack(it)) {
                logical[i] = null;
                giveOrDrop(player, it);
                movedBackpacks++;
                continue;
            }

            if (!plugin.cfg().isAllowedInBackpack(it)) {
                logical[i] = null;
                giveOrDrop(player, it);
                movedBlocked++;
            }
        }

        if (movedBackpacks > 0 || movedBlocked > 0) {
            holder.data().contentsBytes(ItemStackCodec.toBytes(logical));
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }

        return new EjectResult(movedBackpacks, movedBlocked);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (player == null || ItemStacks.isAir(item))
            return;
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private boolean isBackpackHotbarSwap(Player player, InventoryClickEvent e) {
        if (player == null || e == null)
            return false;
        int btn = e.getHotbarButton();
        if (btn < 0 || btn > 8)
            return false;
        ItemStack hotbar = player.getInventory().getItem(btn);
        return isBackpack(hotbar);
    }

    private boolean isBackpack(ItemStack item) {
        if (ItemStacks.isAir(item) || !item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Keys keys = plugin.keys();
        return pdc.has(keys.BACKPACK_ID, PersistentDataType.STRING)
                && pdc.has(keys.BACKPACK_TYPE, PersistentDataType.STRING);
    }

    private boolean isRestockModule(ModuleScreenHolder msh, BackpackData data) {
        if (msh == null || data == null)
            return false;
        byte[] snap = data.installedSnapshots().get(msh.moduleId());
        if (snap == null || snap.length == 0)
            return false;
        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(snap);
        } catch (Exception ex) {
            return false;
        }
        if (arr.length == 0 || arr[0] == null || !arr[0].hasItemMeta())
            return false;
        ItemMeta meta = arr[0].getItemMeta();
        if (meta == null)
            return false;
        String type = meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        return type != null && type.equalsIgnoreCase("Restock");
    }

    private byte[] mergeRestockState(BackpackData data, UUID moduleId, ScreenType screenType, ItemStack[] viewItems) {
        ItemStack[] stored = readRestockStoredState(data, moduleId);

        if (screenType == ScreenType.DROPPER) {
            for (int i = 0; i < 9; i++) {
                stored[i] = (viewItems != null && i < viewItems.length) ? sanitizeGhost(viewItems[i]) : null;
            }
        } else if (screenType == ScreenType.HOPPER) {
            int threshold = 16;
            if (viewItems != null && viewItems.length > 2 && ItemStacks.isNotAir(viewItems[2])) {
                int amt = viewItems[2].getAmount();
                if (amt > 0)
                    threshold = Math.max(1, Math.min(64, amt));
            }
            stored[9] = makeRestockThresholdMarker(threshold);
        }

        return ItemStackCodec.toBytes(stored);
    }

    private ItemStack[] readRestockStoredState(BackpackData data, UUID moduleId) {
        ItemStack[] out = new ItemStack[10];

        int threshold = plugin.getConfig().getInt("Upgrades.Restock.RestockThreshold", 16);
        threshold = Math.max(1, Math.min(64, threshold));
        out[9] = makeRestockThresholdMarker(threshold);

        if (data == null || moduleId == null)
            return out;
        byte[] existing = data.moduleStates().get(moduleId);
        if (existing == null || existing.length == 0)
            return out;

        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(existing);
        } catch (Exception ex) {
            return out;
        }

        int limit = Math.min(9, arr.length);
        for (int i = 0; i < limit; i++) {
            out[i] = sanitizeGhost(arr[i]);
        }

        if (arr.length > 9 && ItemStacks.isNotAir(arr[9])) {
            int amt = arr[9].getAmount();
            if (amt > 0)
                out[9] = makeRestockThresholdMarker(amt);
        } else if (arr.length > 2 && ItemStacks.isNotAir(arr[2])) {
            int amt = arr[2].getAmount();
            if (amt > 0)
                out[9] = makeRestockThresholdMarker(amt);
        }

        return out;
    }

    private ItemStack sanitizeGhost(ItemStack it) {
        if (ItemStacks.isAir(it))
            return null;
        ItemStack s = it.clone();
        s.setAmount(1);
        return s;
    }

    private ItemStack makeRestockThresholdMarker(int threshold) {
        int t = Math.max(1, Math.min(64, threshold));
        ItemStack marker = new ItemStack(org.bukkit.Material.CHEST);
        marker.setAmount(t);
        ItemMeta meta = marker.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("Restock Threshold"));
            marker.setItemMeta(meta);
        }
        return marker;
    }

    private record EjectResult(int backpacks, int blocked) {
    }
}
