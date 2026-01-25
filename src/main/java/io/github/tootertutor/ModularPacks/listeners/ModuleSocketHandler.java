package io.github.tootertutor.ModularPacks.listeners;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.Placeholders;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.gui.ScreenRouter;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.modules.TankModuleLogic;
import io.github.tootertutor.ModularPacks.modules.TankStateCodec;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Handles all module socket interactions: installation, removal, toggling,
 * and module-specific actions (Tank, Feeding, Jukebox, Restock).
 */
public final class ModuleSocketHandler {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;
    private final BackpackItems backpackItems;
    private final ScreenRouter screens;
    private final BackpackSaveManager saveManager;

    public ModuleSocketHandler(ModularPacksPlugin plugin, BackpackMenuRenderer renderer, ScreenRouter screens,
            BackpackSaveManager saveManager) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.backpackItems = new BackpackItems(plugin);
        this.screens = screens;
        this.saveManager = saveManager;
    }

    public void handleUpgradeSocketClick(
            InventoryClickEvent e,
            Player player,
            BackpackMenuHolder holder,
            int invSlot) {
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        ClickType click = e.getClick();

        // Cursor has an item
        if (ItemStacks.isNotAir(cursor)) {
            // INSTALL: cursor has module, socket empty
            if (isModuleItem(cursor) && isEmptySocket(clicked)) {
                installModuleFromCursor(player, holder, invSlot, cursor);
                renderer.render(holder);
                return;
            }

            // TANK: bucket interactions on the installed tank module
            if (isTankModule(clicked)) {
                renderer.saveVisibleStorageToData(holder);
                ItemStack updated = handleTankCursorClick(player, holder, clicked, cursor);
                if (updated != null) {
                    e.setCurrentItem(updated);
                    updateModuleSnapshot(holder, updated);
                    saveManager.scheduleSave(player, holder);
                    renderer.render(holder);
                }
                return;
            }
            return;
        }

        // From here on: cursor is empty
        if (ItemStacks.isAir(clicked))
            return;

        if (!isModuleItem(clicked))
            return;

        // REMOVE (Shift+Right)
        if (click == ClickType.SHIFT_RIGHT) {
            removeModuleToPlayer(player, holder, invSlot);
            renderer.saveVisibleStorageToData(holder);
            renderer.render(holder);
            return;
        }

        // TOGGLE (Shift+Left)
        if (click == ClickType.SHIFT_LEFT) {
            ItemStack updated = toggleModule(holder, clicked);
            if (updated != null) {
                e.setCurrentItem(updated);
                updateModuleSnapshot(holder, updated);
            }
            saveManager.scheduleSave(player, holder);
            renderer.saveVisibleStorageToData(holder);
            renderer.render(holder);
            return;
        }

        // MODULE ACTIONS (Tank: +1/-1 levels)
        if (isTankModule(clicked)) {
            if (click == ClickType.RIGHT) {
                String type = getModuleType(clicked);
                var def = plugin.cfg().findUpgrade(type);
                if (def == null || !def.secondaryAction())
                    return;
            }

            renderer.saveVisibleStorageToData(holder);
            ItemStack updated = handleTankEmptyCursorClick(player, holder, clicked, click);
            if (updated != null) {
                e.setCurrentItem(updated);
                updateModuleSnapshot(holder, updated);
                saveManager.scheduleSave(player, holder);
                renderer.render(holder);
            }
            return;
        }

        // SECONDARY ACTION (Feeding: cycle behavior settings)
        if (click == ClickType.RIGHT && isFeedingModule(clicked)) {
            String type = getModuleType(clicked);
            var def = plugin.cfg().findUpgrade(type);
            if (def == null || !def.secondaryAction())
                return;

            if (cycleFeedingSettings(clicked)) {
                refreshModuleVisuals(holder, clicked);
                updateModuleSnapshot(holder, clicked);
                saveManager.scheduleSave(player, holder);
                renderer.saveVisibleStorageToData(holder);
                renderer.render(holder);
            }
            return;
        }

        // SECONDARY ACTION (Jukebox: cycle playback mode)
        if (click == ClickType.RIGHT && isJukeboxModule(clicked)) {
            String type = getModuleType(clicked);
            var def = plugin.cfg().findUpgrade(type);
            if (def == null || !def.secondaryAction())
                return;

            if (cycleJukeboxMode(clicked)) {
                refreshModuleVisuals(holder, clicked);
                updateModuleSnapshot(holder, clicked);
                saveManager.scheduleSave(player, holder);
                renderer.saveVisibleStorageToData(holder);
                renderer.render(holder);
            }
            return;
        }

        // Restock: Primary=Whitelist (Dropper), Secondary=Threshold (Hopper)
        if (isRestockModule(clicked)) {
            if (click == ClickType.LEFT) {
                openRestockScreen(player, holder, clicked, ScreenType.DROPPER);
                return;
            }

            if (click == ClickType.RIGHT) {
                String type = getModuleType(clicked);
                var def = plugin.cfg().findUpgrade(type);
                if (def == null || !def.secondaryAction())
                    return;
                openRestockScreen(player, holder, clicked, ScreenType.HOPPER);
                return;
            }
        }

        // OPEN MODULE UI
        if (click == ClickType.LEFT) {
            openModuleScreen(player, holder, clicked);
            return;
        }

        // Only open on right-click if this module opts into a secondary action.
        if (click == ClickType.RIGHT) {
            String type = getModuleType(clicked);
            var def = plugin.cfg().findUpgrade(type);
            if (def != null && def.secondaryAction()) {
                openModuleScreen(player, holder, clicked);
            }
        }
    }

    public void refreshBackpackItemsFor(Player player, BackpackMenuHolder holder) {
        if (player == null || holder == null)
            return;
        var inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        if (contents == null || contents.length == 0)
            return;

        boolean changed = false;
        UUID target = holder.backpackId();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!isBackpack(it))
                continue;
            UUID id = readBackpackId(it);
            if (id == null || !id.equals(target))
                continue;

            if (backpackItems.refreshInPlace(it, holder.type(), target, holder.data(), holder.logicalSlots())) {
                inv.setItem(i, it);
                changed = true;
            }
        }

        if (changed) {
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
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

    private UUID readBackpackId(ItemStack item) {
        if (ItemStacks.isAir(item) || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        String idStr = meta.getPersistentDataContainer().get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING);
        if (idStr == null || idStr.isBlank())
            return null;
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isTankModule(ItemStack moduleItem) {
        String type = getModuleType(moduleItem);
        return type != null && type.equalsIgnoreCase("Tank");
    }

    private boolean isFeedingModule(ItemStack moduleItem) {
        String type = getModuleType(moduleItem);
        return type != null && type.equalsIgnoreCase("Feeding");
    }

    private boolean isJukeboxModule(ItemStack moduleItem) {
        String type = getModuleType(moduleItem);
        return type != null && type.equalsIgnoreCase("Jukebox");
    }

    private boolean isRestockModule(ItemStack moduleItem) {
        String type = getModuleType(moduleItem);
        return type != null && type.equalsIgnoreCase("Restock");
    }

    private void openRestockScreen(Player player, BackpackMenuHolder holder, ItemStack moduleItem, ScreenType screen) {
        if (player == null || holder == null || moduleItem == null || screen == null)
            return;

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return;

        Keys keys = plugin.keys();
        String idStr = meta.getPersistentDataContainer().get(keys.MODULE_ID, PersistentDataType.STRING);
        if (idStr == null)
            return;

        UUID moduleId;
        try {
            moduleId = UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            return;
        }

        saveManager.flushSaveNow(player, holder);
        screens.open(player, holder.backpackId(), holder.type().id(), moduleId, screen);
    }

    private boolean cycleJukeboxMode(ItemStack moduleItem) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return false;

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return false;

        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();

        JukeboxMode current = JukeboxMode.fromString(
                pdc.get(keys.MODULE_JUKEBOX_MODE, PersistentDataType.STRING),
                plugin.getConfig().getString("Upgrades.Jukebox.Mode", "RepeatAll"));
        JukeboxMode next = current.next();

        pdc.set(keys.MODULE_JUKEBOX_MODE, PersistentDataType.STRING, next.name());
        moduleItem.setItemMeta(meta);
        return true;
    }

    private boolean cycleFeedingSettings(ItemStack moduleItem) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return false;

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return false;

        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();

        FeedingSelectionMode mode = FeedingSelectionMode.fromString(
                pdc.get(keys.MODULE_FEEDING_SELECTION_MODE, PersistentDataType.STRING),
                plugin.getConfig().getString("Upgrades.Feeding.SelectionMode", "BestCandidate"));
        FeedingPreference pref = FeedingPreference.fromString(
                pdc.get(keys.MODULE_FEEDING_PREFERENCE, PersistentDataType.STRING),
                plugin.getConfig().getString("Upgrades.Feeding.Preference", "Nutrition"));

        FeedingSettings next = FeedingSettings.next(mode, pref);

        pdc.set(keys.MODULE_FEEDING_SELECTION_MODE, PersistentDataType.STRING, next.mode().name());
        pdc.set(keys.MODULE_FEEDING_PREFERENCE, PersistentDataType.STRING, next.preference().name());

        moduleItem.setItemMeta(meta);
        return true;
    }

    private ItemStack handleTankCursorClick(Player player, BackpackMenuHolder holder, ItemStack moduleItem,
            ItemStack cursor) {
        if (player == null || holder == null || moduleItem == null || cursor == null)
            return null;

        UUID moduleId = readModuleId(moduleItem);
        if (moduleId == null)
            return null;

        TankStateCodec.State state = TankStateCodec.decode(readModuleState(holder, moduleId, moduleItem));

        Material cursorMat = cursor.getType();
        if (TankModuleLogic.isSupportedFluidBucket(cursorMat)) {
            return tankDepositFluid(player, holder, moduleId, moduleItem, cursor, state, cursorMat);
        }
        if (cursorMat == Material.BUCKET) {
            return tankWithdrawFluid(player, holder, moduleId, moduleItem, cursor, state);
        }
        return null;
    }

    private ItemStack handleTankEmptyCursorClick(Player player, BackpackMenuHolder holder, ItemStack moduleItem,
            ClickType click) {
        if (player == null || holder == null || moduleItem == null || click == null)
            return null;

        UUID moduleId = readModuleId(moduleItem);
        if (moduleId == null)
            return null;

        TankStateCodec.State state = TankStateCodec.decode(readModuleState(holder, moduleId, moduleItem));

        if (click == ClickType.RIGHT) {
            if (state.fluidBuckets <= 0 && state.expLevels <= 0) {
                state.expMode = !state.expMode;
                return persistTankState(holder, moduleId, moduleItem, state);
            }

            if (state.expMode && state.expLevels > 0) {
                state.expLevels--;
                player.giveExpLevels(1);
                return persistTankState(holder, moduleId, moduleItem, state);
            }

            return null;
        }

        if (click == ClickType.LEFT) {
            if (!state.expMode)
                return null;
            if (state.expLevels >= TankModuleLogic.MAX_EXP_LEVELS)
                return null;
            if (player.getLevel() <= 0)
                return null;

            state.expLevels++;
            player.giveExpLevels(-1);
            return persistTankState(holder, moduleId, moduleItem, state);
        }

        return null;
    }

    private ItemStack tankDepositFluid(
            Player player,
            BackpackMenuHolder holder,
            UUID moduleId,
            ItemStack moduleItem,
            ItemStack cursor,
            TankStateCodec.State state,
            Material fluidBucket) {
        if (state.expMode || state.expLevels > 0)
            return null;
        if (state.fluidBuckets >= TankModuleLogic.MAX_FLUID_BUCKETS)
            return null;

        String curFluid = state.fluidBucketMaterial;
        if (state.fluidBuckets > 0 && curFluid != null && !curFluid.equalsIgnoreCase(fluidBucket.name()))
            return null;

        state.fluidBucketMaterial = fluidBucket.name();
        state.fluidBuckets++;

        ItemStack newCursor = cursor.clone();
        if (newCursor.getAmount() <= 1) {
            player.setItemOnCursor(new ItemStack(Material.BUCKET, 1));
        } else {
            newCursor.setAmount(newCursor.getAmount() - 1);
            player.setItemOnCursor(newCursor);
            giveOrDrop(player, new ItemStack(Material.BUCKET, 1));
        }

        ItemStack updated = persistTankState(holder, moduleId, moduleItem, state);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        return updated;
    }

    private ItemStack tankWithdrawFluid(
            Player player,
            BackpackMenuHolder holder,
            UUID moduleId,
            ItemStack moduleItem,
            ItemStack cursor,
            TankStateCodec.State state) {
        if (state.expMode || state.expLevels > 0)
            return null;
        if (state.fluidBuckets <= 0)
            return null;

        Material fluidBucket = state.fluidBucketMaterial == null ? null
                : Material.matchMaterial(state.fluidBucketMaterial);
        if (fluidBucket == null || !TankModuleLogic.isSupportedFluidBucket(fluidBucket))
            return null;

        ItemStack newCursor = cursor.clone();
        if (newCursor.getAmount() <= 1) {
            player.setItemOnCursor(new ItemStack(fluidBucket, 1));
        } else {
            newCursor.setAmount(newCursor.getAmount() - 1);
            player.setItemOnCursor(newCursor);
            giveOrDrop(player, new ItemStack(fluidBucket, 1));
        }

        state.fluidBuckets--;
        if (state.fluidBuckets <= 0) {
            state.fluidBuckets = 0;
            state.fluidBucketMaterial = null;
        }

        ItemStack updated = persistTankState(holder, moduleId, moduleItem, state);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        return updated;
    }

    private byte[] readModuleState(BackpackMenuHolder holder, UUID moduleId, ItemStack moduleItem) {
        byte[] fromHolder = holder.data().moduleStates().get(moduleId);
        if (fromHolder != null)
            return fromHolder;
        return readModuleStateFromItem(moduleItem);
    }

    private UUID readModuleId(ItemStack moduleItem) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return null;
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return null;
        String idStr = meta.getPersistentDataContainer().get(plugin.keys().MODULE_ID, PersistentDataType.STRING);
        if (idStr == null)
            return null;
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ItemStack persistTankState(BackpackMenuHolder holder, UUID moduleId, ItemStack moduleItem,
            TankStateCodec.State state) {
        if (state.expLevels > 0) {
            state.expMode = true;
            state.fluidBuckets = 0;
            state.fluidBucketMaterial = null;
        }
        if (state.fluidBuckets > 0) {
            state.expMode = false;
            state.expLevels = 0;
        }

        state.fluidBuckets = Math.max(0, Math.min(TankModuleLogic.MAX_FLUID_BUCKETS, state.fluidBuckets));
        state.expLevels = Math.max(0, Math.min(TankModuleLogic.MAX_EXP_LEVELS, state.expLevels));

        byte[] bytes = TankStateCodec.encode(state);
        holder.data().moduleStates().put(moduleId, bytes);

        writeModuleStateToItem(moduleItem, bytes);

        return TankModuleLogic.applyVisuals(plugin, moduleItem, state);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (player == null || ItemStacks.isAir(item))
            return;
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private boolean isModuleItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Keys keys = plugin.keys();
        return pdc.has(keys.MODULE_ID, PersistentDataType.STRING)
                && pdc.has(keys.MODULE_TYPE, PersistentDataType.STRING);
    }

    private void installModuleFromCursor(Player player, BackpackMenuHolder holder, int invSlot, ItemStack cursor) {
        Keys keys = plugin.keys();

        ItemMeta meta = cursor.getItemMeta();
        if (meta == null)
            return;

        String moduleType = meta.getPersistentDataContainer().get(keys.MODULE_TYPE, PersistentDataType.STRING);
        if (moduleType == null)
            return;

        if (isModuleTypeInstalled(holder, moduleType)) {
            playSocketFail(player);
            return;
        }

        String idStr = meta.getPersistentDataContainer().get(keys.MODULE_ID, PersistentDataType.STRING);
        if (idStr == null)
            return;

        UUID moduleId = UUID.fromString(idStr);

        int socketIndex = holder.upgradeSlots().indexOf(invSlot);
        if (socketIndex < 0)
            return;

        renderer.saveVisibleStorageToData(holder);

        holder.data().installedModules().put(socketIndex, moduleId);

        byte[] importedState = readModuleStateFromItem(cursor);
        if (moduleType.equalsIgnoreCase("Tank") && importedState == null) {
            importedState = TankStateCodec.encode(new TankStateCodec.State());
        }
        if (importedState != null) {
            holder.data().moduleStates().put(moduleId, importedState);
            if (moduleType.equalsIgnoreCase("Tank")) {
                TankStateCodec.State tankState = TankStateCodec.decode(importedState);
                cursor = TankModuleLogic.applyVisuals(plugin, cursor, tankState);
            }
        }

        if (!moduleType.equalsIgnoreCase("Tank")) {
            applyModuleLore(cursor);
        }

        holder.data().installedSnapshots().put(moduleId, ItemStackCodec.toBytes(new ItemStack[] { cursor.clone() }));

        player.setItemOnCursor(null);

        saveManager.scheduleSave(player, holder);
        refreshBackpackItemsFor(player, holder);
        playSocketSuccess(player);
    }

    private void removeModuleToPlayer(Player player, BackpackMenuHolder holder, int invSlot) {
        int socketIndex = holder.upgradeSlots().indexOf(invSlot);
        if (socketIndex < 0)
            return;

        UUID moduleId = holder.data().installedModules().get(socketIndex);
        if (moduleId == null)
            return;

        ItemStack item = null;
        byte[] snap = holder.data().installedSnapshots().get(moduleId);
        if (snap != null) {
            ItemStack[] arr = ItemStackCodec.fromBytes(snap);
            if (arr.length > 0)
                item = arr[0];
        }

        byte[] state = holder.data().moduleStates().get(moduleId);
        if (item != null) {
            writeModuleStateToItem(item, state);
            if (isTankModule(item)) {
                TankStateCodec.State tankState = TankStateCodec.decode(state);
                item = TankModuleLogic.applyVisuals(plugin, item, tankState);
            } else {
                applyModuleLore(item);
            }
        }

        holder.data().installedModules().remove(socketIndex);
        holder.data().installedSnapshots().remove(moduleId);
        holder.data().moduleStates().remove(moduleId);

        if (item != null)
            giveOrDrop(player, item);

        saveManager.scheduleSave(player, holder);
        refreshBackpackItemsFor(player, holder);
    }

    private ItemStack toggleModule(BackpackMenuHolder holder, ItemStack moduleItem) {
        if (holder == null || moduleItem == null)
            return null;
        Keys keys = plugin.keys();
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return null;

        Byte enabled = meta.getPersistentDataContainer().get(keys.MODULE_ENABLED, PersistentDataType.BYTE);
        byte newVal = (enabled != null && enabled == 1) ? (byte) 0 : (byte) 1;

        meta.getPersistentDataContainer().set(keys.MODULE_ENABLED, PersistentDataType.BYTE, newVal);
        moduleItem.setItemMeta(meta);

        return refreshModuleVisuals(holder, moduleItem);
    }

    private ItemStack refreshModuleVisuals(BackpackMenuHolder holder, ItemStack moduleItem) {
        if (ItemStacks.isAir(moduleItem) || !moduleItem.hasItemMeta())
            return null;

        String type = getModuleType(moduleItem);
        if (type == null)
            return null;

        if (type.equalsIgnoreCase("Tank")) {
            UUID moduleId = readModuleId(moduleItem);
            if (moduleId != null) {
                TankStateCodec.State state = TankStateCodec.decode(readModuleState(holder, moduleId, moduleItem));
                return TankModuleLogic.applyVisuals(plugin, moduleItem, state);
            }
        }

        applyModuleLore(moduleItem);
        return moduleItem;
    }

    private void applyModuleLore(ItemStack moduleItem) {
        if (ItemStacks.isAir(moduleItem) || !moduleItem.hasItemMeta())
            return;

        String type = getModuleType(moduleItem);
        if (type == null)
            return;

        var def = plugin.cfg().findUpgrade(type);
        if (def == null)
            return;

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return;

        meta.displayName(Text.c(Placeholders.expandText(plugin, def, moduleItem, def.displayName())));
        List<String> expanded = Placeholders.expandLore(plugin, def, moduleItem, def.lore());
        meta.lore(Text.lore(expanded));
        moduleItem.setItemMeta(meta);
    }

    private void updateModuleSnapshot(BackpackMenuHolder holder, ItemStack moduleItem) {
        Keys keys = plugin.keys();
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return;

        String idStr = meta.getPersistentDataContainer().get(keys.MODULE_ID, PersistentDataType.STRING);
        if (idStr == null)
            return;

        UUID moduleId = UUID.fromString(idStr);
        holder.data().installedSnapshots().put(moduleId,
                ItemStackCodec.toBytes(new ItemStack[] { moduleItem.clone() }));
    }

    private void openModuleScreen(Player player, BackpackMenuHolder holder, ItemStack moduleItem) {
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return;

        Keys keys = plugin.keys();
        String moduleType = meta.getPersistentDataContainer().get(keys.MODULE_TYPE, PersistentDataType.STRING);
        if (moduleType == null)
            return;

        var def = plugin.cfg().findUpgrade(moduleType);
        if (def == null)
            return;

        String idStr = meta.getPersistentDataContainer().get(keys.MODULE_ID, PersistentDataType.STRING);
        if (idStr == null)
            return;

        UUID moduleId = UUID.fromString(idStr);

        ScreenType screenType = def.screenType();
        if (screenType == ScreenType.NONE) {
            player.sendMessage(Text.c("&cThis module has no configurable UI."));
            return;
        }

        saveManager.flushSaveNow(player, holder);
        screens.open(player, holder.backpackId(), holder.type().id(), moduleId, screenType);
    }

    private String getModuleType(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        String type = meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        return type;
    }

    private boolean isModuleTypeInstalled(BackpackMenuHolder holder, String moduleType) {
        if (moduleType == null)
            return false;

        for (UUID moduleId : holder.data().installedModules().values()) {
            byte[] snap = holder.data().installedSnapshots().get(moduleId);
            if (snap == null)
                continue;

            ItemStack[] arr = ItemStackCodec.fromBytes(snap);
            if (arr.length == 0 || arr[0] == null)
                continue;

            String type = getModuleType(arr[0]);
            if (type != null && type.equalsIgnoreCase(moduleType))
                return true;
        }
        return false;
    }

    private boolean isEmptySocket(ItemStack item) {
        if (ItemStacks.isAir(item))
            return true;
        return !isModuleItem(item);
    }

    private byte[] readModuleStateFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return null;

        ItemMeta meta = item.getItemMeta();
        String b64 = meta.getPersistentDataContainer().get(plugin.keys().MODULE_STATE_B64, PersistentDataType.STRING);
        if (b64 == null || b64.isBlank())
            return null;

        try {
            return java.util.Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeModuleStateToItem(ItemStack item, byte[] state) {
        if (item == null)
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        var pdc = meta.getPersistentDataContainer();

        if (state == null || state.length == 0) {
            pdc.remove(plugin.keys().MODULE_STATE_B64);
        } else {
            String b64 = java.util.Base64.getEncoder().encodeToString(state);
            pdc.set(plugin.keys().MODULE_STATE_B64, PersistentDataType.STRING, b64);
        }

        item.setItemMeta(meta);
    }

    private void playSocketSuccess(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.0f);
    }

    private void playSocketFail(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
    }

    // Enums and records for module settings

    private enum JukeboxMode {
        SHUFFLE("Shuffle"),
        REPEAT_ONE("Repeat One"),
        REPEAT_ALL("Repeat All");

        JukeboxMode(String displayName) {
        }

        static JukeboxMode fromString(String raw, String fallbackRaw) {
            JukeboxMode parsed = parse(raw);
            if (parsed != null)
                return parsed;
            parsed = parse(fallbackRaw);
            if (parsed != null)
                return parsed;
            return REPEAT_ALL;
        }

        private static JukeboxMode parse(String raw) {
            if (raw == null)
                return null;
            String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (s.isEmpty())
                return null;
            return switch (s) {
                case "SHUFFLE", "RANDOM" -> SHUFFLE;
                case "REPEAT_ONE", "REPEAT1", "ONE" -> REPEAT_ONE;
                case "REPEAT_ALL", "REPEATALL", "ALL" -> REPEAT_ALL;
                default -> null;
            };
        }

        public JukeboxMode next() {
            return switch (this) {
                case SHUFFLE -> REPEAT_ONE;
                case REPEAT_ONE -> REPEAT_ALL;
                case REPEAT_ALL -> SHUFFLE;
            };
        }
    }

    private enum FeedingSelectionMode {
        BEST_CANDIDATE("Best Candidate"),
        WHITELIST_ORDER("Prefer First in Whitelist");

        FeedingSelectionMode(String displayName) {
        }

        static FeedingSelectionMode fromString(String raw, String fallbackRaw) {
            FeedingSelectionMode parsed = parse(raw);
            if (parsed != null)
                return parsed;
            parsed = parse(fallbackRaw);
            if (parsed != null)
                return parsed;
            return BEST_CANDIDATE;
        }

        private static FeedingSelectionMode parse(String raw) {
            if (raw == null)
                return null;
            String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (s.isEmpty())
                return null;
            return switch (s) {
                case "BEST", "BESTCANDIDATE", "BEST_CANDIDATE" -> BEST_CANDIDATE;
                case "WHITELIST", "WHITELISTORDER", "WHITELIST_ORDER", "PREFER_FIRST_IN_WHITELIST" -> WHITELIST_ORDER;
                default -> null;
            };
        }
    }

    private enum FeedingPreference {
        NUTRITION("Prefer Nutrition"),
        EFFECTS("Prefer Effects");

        FeedingPreference(String displayName) {
        }

        static FeedingPreference fromString(String raw, String fallbackRaw) {
            FeedingPreference parsed = parse(raw);
            if (parsed != null)
                return parsed;
            parsed = parse(fallbackRaw);
            if (parsed != null)
                return parsed;
            return NUTRITION;
        }

        private static FeedingPreference parse(String raw) {
            if (raw == null)
                return null;
            String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (s.isEmpty())
                return null;
            return switch (s) {
                case "NUTRITION" -> NUTRITION;
                case "EFFECT", "EFFECTS" -> EFFECTS;
                default -> null;
            };
        }
    }

    private record FeedingSettings(FeedingSelectionMode mode, FeedingPreference preference) {
        static FeedingSettings next(FeedingSelectionMode mode, FeedingPreference pref) {
            if (mode == null)
                mode = FeedingSelectionMode.BEST_CANDIDATE;
            if (pref == null)
                pref = FeedingPreference.NUTRITION;

            if (mode == FeedingSelectionMode.BEST_CANDIDATE && pref == FeedingPreference.NUTRITION) {
                return new FeedingSettings(FeedingSelectionMode.BEST_CANDIDATE, FeedingPreference.EFFECTS);
            }
            if (mode == FeedingSelectionMode.BEST_CANDIDATE && pref == FeedingPreference.EFFECTS) {
                return new FeedingSettings(FeedingSelectionMode.WHITELIST_ORDER, FeedingPreference.NUTRITION);
            }
            if (mode == FeedingSelectionMode.WHITELIST_ORDER && pref == FeedingPreference.NUTRITION) {
                return new FeedingSettings(FeedingSelectionMode.WHITELIST_ORDER, FeedingPreference.EFFECTS);
            }
            return new FeedingSettings(FeedingSelectionMode.BEST_CANDIDATE, FeedingPreference.NUTRITION);
        }
    }
}
