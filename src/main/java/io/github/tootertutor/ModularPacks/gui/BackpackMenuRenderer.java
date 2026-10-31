package io.github.tootertutor.ModularPacks.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.config.Placeholders;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.modules.tank.TankModuleLogic;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.storage.BackpackStorageService;
import io.github.tootertutor.ModularPacks.storage.StoredStack;
import io.github.tootertutor.ModularPacks.util.Text;
import net.kyori.adventure.text.Component;

public final class BackpackMenuRenderer {

    private final ModularPacksPlugin plugin;

    public BackpackMenuRenderer(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory openMenu(Player player, UUID backpackId, String typeId) {
        var type = plugin.cfg().findType(typeId);
        if (type == null)
            return null;

        BackpackData data = plugin.repo().loadOrCreate(backpackId, type.id());

        BackpackMenuLayout layout = BackpackMenuLayout.create(type.rows(), 0, plugin.cfg().resizeGui());
        boolean paginated = layout.paginated();
        int invSize = layout.inventorySize();
        int pageSize = layout.pageSize();
        List<Integer> upgradeSlots = SlotLayout.upgradeSocketSlots(invSize, type.upgradeSlots(), paginated);

        BackpackMenuHolder holder = new BackpackMenuHolder(
                backpackId,
                type,
                data,
                null,
                paginated,
                pageSize,
                upgradeSlots);

        Component title = buildBackpackTitle(player, type, data);
        Inventory inv = Bukkit.createInventory(holder, invSize, title);
        holder.setInventory(inv);
        holder.viewer(player);

        holder.page(layout.page());

        render(holder);

        player.openInventory(inv);
        return inv;
    }

    public Inventory openMenu(Player player, UUID backpackId, String typeId, int page) {
        var type = plugin.cfg().findType(typeId);
        if (type == null)
            return null;

        BackpackData data = plugin.repo().loadOrCreate(backpackId, type.id());

        BackpackMenuLayout layout = BackpackMenuLayout.create(type.rows(), page, plugin.cfg().resizeGui());
        boolean paginated = layout.paginated();
        int invSize = layout.inventorySize();
        int pageSize = layout.pageSize();
        List<Integer> upgradeSlots = SlotLayout.upgradeSocketSlots(invSize, type.upgradeSlots(), paginated);

        BackpackMenuHolder holder = new BackpackMenuHolder(backpackId, type, data, null, paginated, pageSize,
                upgradeSlots);

        holder.page(layout.page());

        Component title = buildBackpackTitle(player, type, data);
        Inventory inv = Bukkit.createInventory(holder, invSize, title);
        holder.setInventory(inv);
        holder.viewer(player);

        render(holder);

        player.openInventory(inv);
        return inv;
    }

    public void openMenu(Player player, BackpackData data, BackpackTypeDef type, int page) {
        openMenu(player, data, type, page, null);
    }

    public void openMenu(Player player, BackpackData data, BackpackTypeDef type, int page, Location placedLocation) {
        BackpackMenuLayout layout = BackpackMenuLayout.create(type.rows(), page, plugin.cfg().resizeGui());
        boolean paginated = layout.paginated();
        int invSize = layout.inventorySize();
        int pageSize = layout.pageSize();
        List<Integer> upgradeSlots = SlotLayout.upgradeSocketSlots(invSize, type.upgradeSlots(), paginated);

        BackpackMenuHolder holder = new BackpackMenuHolder(
                data.backpackId(),
                type,
                data,
                placedLocation,
                paginated,
                pageSize,
                upgradeSlots);

        holder.page(layout.page());

        Component title = buildBackpackTitle(player, type, data);
        Inventory inv = Bukkit.createInventory(holder, invSize, title);
        holder.setInventory(inv);
        holder.viewer(player);

        render(holder);
        player.openInventory(inv);
    }

    private Component buildBackpackTitle(Player player, BackpackTypeDef type, BackpackData data) {
        String customName = data.backpackName() == null ? "" : data.backpackName().trim();
        if (!customName.isEmpty()) {
            return Text.c(plugin.lang().text(player, "gui.backpack.customTitle", "name", customName, "type",
                    type.displayName()));
        }
        return Text.c(plugin.lang().text(player, "gui.backpack.title", "type", type.displayName()));
    }

    public void render(BackpackMenuHolder holder) {
        Inventory inv = holder.getInventory();
        inv.clear();

        int invSize = inv.getSize();
        int storageSize = SlotLayout.bottomRowStart(invSize);

        int logicalSize = holder.logicalSlots();
        BackpackStorageService storageService = plugin.backpackStorage();
        BackpackStorage storage = storageService.load(holder.data(), logicalSize);

        for (int visibleSlot = 0; visibleSlot < storageSize; visibleSlot++) {
            int logicalIndex = BackpackPageMapping.logicalIndex(
                    holder.paginated(), holder.page(), visibleSlot, logicalSize);
            if (logicalIndex < 0) {
                break;
            }
            StoredStack stored = storage.get(logicalIndex);
            if (stored != null) {
                inv.setItem(visibleSlot, renderStorageStack(holder.viewer(), holder.data(), stored));
            }
        }

        // bottom row (nav row) with fillers + optional buttons + upgrade sockets
        renderNavRow(holder);
        renderUpgradeSockets(holder);

        if (holder.paginated()) {
            int valid = validVisibleSlots(holder, storageSize); // note overload below
            if (valid < storageSize) {
                ItemStack blocked = namedItemWithPdc(
                        plugin.cfg().navBorderFiller(),
                        "&7",
                        plugin.cfg().getGuiItemKey(),
                        "nav-border-filler");
                for (int i = valid; i < storageSize; i++) {
                    inv.setItem(i, blocked);
                }
            }
        }

    }

    /**
     * Compatibility hook retained for callers that flush before page/module
     * changes.
     * Storage interactions are applied directly to logical storage, so rendered
     * display items must never be read back into the canonical model.
     */
    public void saveVisibleStorageToData(BackpackMenuHolder holder) {
        // Intentionally empty: logical storage is authoritative.
    }

    private ItemStack renderStorageStack(Player player, BackpackData data, StoredStack stored) {
        ItemStack prototype = stored.prototype();
        return VirtualStackDisplay.render(
                plugin.backpackStorage(),
                stored,
                plugin.backpackStorage().capacityFor(data, prototype),
                plugin.lang().get(player, "gui.storage.count",
                        plugin.cfg().getString("modularpacks.VirtualStackCountLore",
                                "&7Stored: &f{count} &7/ &f{capacity}")));
    }

    private void renderNavRow(BackpackMenuHolder holder) {
        Player player = holder.viewer();
        Inventory inv = holder.getInventory();
        int invSize = inv.getSize();
        int bottomStart = SlotLayout.bottomRowStart(invSize);

        // Fill bottom row with border fillers marked with PDC
        ItemStack filler = namedItemWithPdc(
                plugin.cfg().navBorderFiller(),
                "&7",
                plugin.cfg().getGuiItemKey(),
                "nav-border-filler");
        for (int slot = bottomStart; slot < invSize; slot++) {
            inv.setItem(slot, filler);
        }

        // Only show prev/next if paginated and valid for current page
        if (holder.paginated()) {
            int pageCount = pageCount(holder);

            int prevSlot = SlotLayout.prevButtonSlot(invSize);
            int nextSlot = SlotLayout.nextButtonSlot(invSize);

            if (holder.page() > 0) {
                inv.setItem(prevSlot, namedItemWithPdc(
                        plugin.cfg().navPageButtons(),
                        plugin.lang().text(player, "gui.navigation.previous"),
                        plugin.cfg().getGuiItemKey(),
                        "nav-page-button"));
            }
            if (holder.page() < pageCount - 1) {
                inv.setItem(nextSlot, namedItemWithPdc(
                        plugin.cfg().navPageButtons(),
                        plugin.lang().text(player, "gui.navigation.next"),
                        plugin.cfg().getGuiItemKey(),
                        "nav-page-button"));
            }
        }

        int sortSlot = SlotLayout.sortButtonSlot(invSize, holder.upgradeSlots(), holder.paginated());
        if (sortSlot >= 0) {
            List<String> lore = new ArrayList<>(plugin.lang().lines(player, "gui.sort.lore", "status",
                    plugin.lang().text(player,
                            holder.sortLocked() ? "gui.navigation.locked" : "gui.navigation.unlocked")));
            for (BackpackSortMode mode : BackpackSortMode.values()) {
                String color = (mode == holder.sortMode()) ? "&a" : "&7";
                lore.add(color + plugin.lang().get(player,
                        "gui.sort.modes." + mode.name().toLowerCase(java.util.Locale.ROOT), mode.displayName()));
            }

            inv.setItem(sortSlot,
                    namedItem(Material.COMPARATOR, plugin.lang().text(player, "gui.navigation.sort"), lore));
        }

        // Render mode button (opposite side of sort button)
        int modeSlot = SlotLayout.modeButtonSlot(invSize, holder.upgradeSlots(), holder.paginated(), sortSlot);
        if (modeSlot >= 0) {
            renderModeButton(player, inv, holder.data(), modeSlot);
        }
    }

    private void renderModeButton(Player player, Inventory inv, BackpackData data, int slot) {
        List<String> lore = new ArrayList<>(plugin.lang().lines(player, "gui.navigation.settingsLore"));

        if (data.isShared()) {
            if (data.isShareHost()) {
                lore.add(plugin.lang().text(player, "gui.navigation.host"));
            } else {
                lore.add(plugin.lang().text(player, "gui.navigation.joined"));
            }
        } else {
            lore.add(plugin.lang().text(player, "gui.navigation.private"));
        }

        inv.setItem(slot, namedItem(Material.REPEATER, plugin.lang().text(player, "gui.navigation.settings"), lore));
    }

    private void renderUpgradeSockets(BackpackMenuHolder holder) {
        Player player = holder.viewer();
        Inventory inv = holder.getInventory();
        Keys keys = plugin.keys();

        // If this backpack type has 0 upgrade slots, nothing to render.
        if (holder.type().upgradeSlots() <= 0)
            return;

        // holder.upgradeSlots() already contains the bottom-row-centered slots
        for (int socketSlotIndex = 0; socketSlotIndex < holder.upgradeSlots().size(); socketSlotIndex++) {
            int invSlot = holder.upgradeSlots().get(socketSlotIndex);

            UUID moduleId = holder.data().installedModules().get(socketSlotIndex);
            if (moduleId == null) {
                List<String> emptyLore = plugin.lang().lines(player, "gui.navigation.emptySocketLore");
                inv.setItem(invSlot,
                        namedItemWithPdc(
                                plugin.cfg().unlockedUpgradeSlotMaterial(),
                                plugin.lang().text(player, "gui.navigation.emptySocket"),
                                emptyLore,
                                plugin.cfg().getGuiItemKey(),
                                "unlocked-upgrade-slot"));
                continue;
            }

            // We only have the snapshot here (fallback). Use it as the displayed item.
            byte[] snap = holder.data().installedSnapshots().get(moduleId);
            ItemStack display = null;

            if (snap != null) {
                ItemStack[] arr = ItemStackCodec.fromBytes(snap);
                if (arr.length > 0)
                    display = arr[0];
            }

            if (display == null) {
                display = namedItem(Material.BARRIER, plugin.lang().text(player, "gui.navigation.missingModule"));
                inv.setItem(invSlot, display);
                continue;
            }

            // Ensure it has module_id on it (if snapshot is old)
            ItemMeta meta = display.getItemMeta();
            if (meta != null && !meta.getPersistentDataContainer().has(keys.MODULE_ID, PersistentDataType.STRING)) {
                meta.getPersistentDataContainer().set(keys.MODULE_ID, PersistentDataType.STRING, moduleId.toString());
                display.setItemMeta(meta);
            }

            // Dynamic visuals for Tank module based on stored state
            ItemMeta meta2 = display.getItemMeta();
            if (meta2 != null) {
                String moduleType = meta2.getPersistentDataContainer().get(keys.MODULE_TYPE, PersistentDataType.STRING);
                if (TankModuleLogic.isTankModuleType(moduleType)) {
                    display = TankModuleLogic.applyVisuals(plugin, display, holder.data().moduleStates().get(moduleId));
                } else if (moduleType != null) {
                    var def = plugin.cfg().findUpgrade(moduleType);
                    if (def != null) {
                        meta2.displayName(
                                Text.c(Placeholders.expandTextFor(plugin, player, def, display, def.displayName())));
                        meta2.lore(Text.lore(Placeholders.expandLoreFor(plugin, player, def, display, def.lore())));
                        display.setItemMeta(meta2);
                    }
                }
            }

            inv.setItem(invSlot, display);
        }
    }

    private int validVisibleSlots(BackpackMenuHolder holder, int storageSize) {
        if (!holder.paginated())
            return Math.min(holder.logicalSlots(), storageSize);

        int remaining = holder.logicalSlots() - holder.page() * 45;
        int valid = Math.max(0, Math.min(remaining, 45)); // logical remaining in this page
        return Math.min(valid, storageSize); // can't exceed this inventory's storage area
    }

    private int pageCount(BackpackMenuHolder holder) {
        if (!holder.paginated())
            return 1;
        int logical = holder.logicalSlots();
        return Math.max(1, (int) Math.ceil(logical / 45.0));
    }

    private static ItemStack namedItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack namedItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            meta.lore(Text.lore(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create a named item with PDC data marker.
     * 
     * @param mat      The material
     * @param name     The display name
     * @param pdcKey   The NamespacedKey for the PDC marker
     * @param pdcValue The string value to store in PDC
     * @return ItemStack with PDC marker applied
     */
    private ItemStack namedItemWithPdc(Material mat, String name, NamespacedKey pdcKey, String pdcValue) {
        ItemStack item = namedItem(mat, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, pdcValue);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create a named item with lore and PDC data marker.
     * 
     * @param mat      The material
     * @param name     The display name
     * @param lore     The lore lines
     * @param pdcKey   The NamespacedKey for the PDC marker
     * @param pdcValue The string value to store in PDC
     * @return ItemStack with PDC marker applied
     */
    private ItemStack namedItemWithPdc(Material mat, String name, List<String> lore,
            NamespacedKey pdcKey, String pdcValue) {
        ItemStack item = namedItem(mat, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, pdcValue);
            item.setItemMeta(meta);
        }
        return item;
    }
}
