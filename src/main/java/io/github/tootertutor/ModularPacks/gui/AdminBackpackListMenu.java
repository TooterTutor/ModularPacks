package io.github.tootertutor.ModularPacks.gui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository.BackpackSummary;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenuHolder.AdminBackpackListEntry;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenuHolder.InteractionMode;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;
import net.kyori.adventure.text.Component;

public final class AdminBackpackListMenu {

    private static final int INVENTORY_SIZE = 54;
    private static final int NAV_PREV_SLOT = 45;
    private static final int NAV_MODE_SLOT = 46;
    private static final int NAV_SORT_SLOT = 52;
    private static final int NAV_NEXT_SLOT = 53;

    private static final String GUI_PREV = "admin-list-prev";
    private static final String GUI_NEXT = "admin-list-next";
    private static final String GUI_MODE = "admin-list-mode";
    private static final String GUI_SORT = "admin-list-sort";
    private static final String GUI_OPEN_PREFIX = "admin-list-open:";

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    public AdminBackpackListMenu(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
    }

    public void openMenu(org.bukkit.entity.Player viewer, OfflinePlayer target, List<BackpackSummary> rows) {
        if (viewer == null || target == null) {
            return;
        }

        String ownerName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        List<AdminBackpackListEntry> entries = buildEntries(rows);
        AdminBackpackListMenuHolder holder = new AdminBackpackListMenuHolder(target.getUniqueId(), ownerName, entries,
                0);

        Component title = Text.c("&8Backpacks: &7" + ownerName);
        Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.setInventory(inv);

        render(holder);
        viewer.openInventory(inv);
    }

    public void render(AdminBackpackListMenuHolder holder) {
        Inventory inv = holder.getInventory();
        if (inv == null) {
            return;
        }

        inv.clear();

        List<AdminBackpackListEntry> rows = sortedEntries(holder);
        int start = holder.page() * holder.pageSize();
        int end = Math.min(rows.size(), start + holder.pageSize());

        Map<String, Integer> typeCounter = new HashMap<>();
        for (int i = 0; i < start; i++) {
            incrementTypeCounter(typeCounter, safeType(rows.get(i).backpackType()));
        }

        int slot = 0;
        for (int i = start; i < end; i++) {
            AdminBackpackListEntry row = rows.get(i);
            String typeKey = safeType(row.backpackType());
            int index = incrementTypeCounter(typeCounter, typeKey);
            inv.setItem(slot++, buildBackpackEntry(row, index));
        }

        renderNavRow(holder, inv);
    }

    public boolean isPreviousButton(ItemStack item) {
        return hasGuiValue(item, GUI_PREV);
    }

    public boolean isNextButton(ItemStack item) {
        return hasGuiValue(item, GUI_NEXT);
    }

    public boolean isSortButton(ItemStack item) {
        return hasGuiValue(item, GUI_SORT);
    }

    public boolean isModeButton(ItemStack item) {
        return hasGuiValue(item, GUI_MODE);
    }

    public UUID extractBackpackId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String value = meta.getPersistentDataContainer().get(plugin.cfg().getGuiItemKey(), PersistentDataType.STRING);
        if (value == null || !value.startsWith(GUI_OPEN_PREFIX)) {
            return null;
        }

        String uuidText = value.substring(GUI_OPEN_PREFIX.length());
        try {
            return UUID.fromString(uuidText);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void renderNavRow(AdminBackpackListMenuHolder holder, Inventory inv) {
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) {
            inv.setItem(slot, namedItemWithGuiMarker(plugin.cfg().navBorderFiller(), "&7", "admin-list-filler"));
        }

        int page = holder.page() + 1;
        int pageCount = holder.pageCount();

        List<String> modeLore = List.of(
                "&7Current mode: &f" + holder.mode().name().toLowerCase(),
                "&eClick to toggle mode",
                "&8View: open backpack",
                "&8Recover: left->you, right->owner");
        String modeName = holder.mode() == InteractionMode.VIEW ? "&bMode: View" : "&dMode: Recover";
        inv.setItem(NAV_MODE_SLOT, namedItemWithGuiMarker(Material.COMPARATOR, modeName, modeLore, GUI_MODE));

        List<String> sortLore = List.of(
                "&7Field: &f" + holder.sortField().label(),
                "&7Direction: &f" + (holder.ascending() ? "Ascending" : "Descending"),
                "&eLeft-click: next field",
                "&eRight-click: toggle direction");
        inv.setItem(NAV_SORT_SLOT, namedItemWithGuiMarker(Material.HOPPER, "&bSort", sortLore, GUI_SORT));

        if (holder.page() > 0) {
            List<String> lore = List.of(
                    "&7Page: &f" + page + "&7/&f" + pageCount,
                    "&eClick to view previous page");
            inv.setItem(NAV_PREV_SLOT,
                    namedItemWithGuiMarker(plugin.cfg().navPageButtons(), "&aPrevious Page", lore, GUI_PREV));
        }

        if (holder.page() + 1 < pageCount) {
            List<String> lore = List.of(
                    "&7Page: &f" + page + "&7/&f" + pageCount,
                    "&eClick to view next page");
            inv.setItem(NAV_NEXT_SLOT,
                    namedItemWithGuiMarker(plugin.cfg().navPageButtons(), "&aNext Page", lore, GUI_NEXT));
        }
    }

    private ItemStack buildBackpackEntry(AdminBackpackListEntry row, int index) {
        ItemStack item;
        try {
            item = backpackItems.createExisting(row.backpackId(), row.backpackType());
        } catch (Exception ex) {
            item = new ItemStack(Material.CHEST);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String customName = row.backpackName() == null || row.backpackName().isBlank() ? "" : row.backpackName().trim();
        String title = customName.isEmpty()
                ? "&e" + safeType(row.backpackType()) + " &7#" + index
                : "&e" + customName + " &7(" + safeType(row.backpackType()) + " #" + index + ")";
        meta.displayName(Text.c(title));

        List<String> lore = new ArrayList<>();
        lore.add("&7Type: &f" + safeType(row.backpackType()));
        lore.add("&7Name: &f" + (customName.isEmpty() ? "(default)" : customName));
        lore.add("&7Items: &f" + row.itemCount());
        lore.add("&7Modules: &f" + row.moduleCount());
        lore.add("&7Location: &f" + (row.placed() ? row.locationText() : "Not placed"));
        lore.add("&7Last Accessed: &f" + formatTimestamp(row.lastAccessedMillis()));
        lore.add("&7Backpack ID: &f" + row.backpackId());
        lore.add("&8");
        lore.add("&aLeft-click: open/recover to you");
        lore.add("&bRight-click: recover to owner (recover mode)");

        meta.lore(Text.lore(lore));
        meta.getPersistentDataContainer().set(
                plugin.cfg().getGuiItemKey(),
                PersistentDataType.STRING,
                GUI_OPEN_PREFIX + row.backpackId());
        item.setItemMeta(meta);
        return item;
    }

    private List<AdminBackpackListEntry> buildEntries(List<BackpackSummary> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<AdminBackpackListEntry> out = new ArrayList<>(rows.size());
        for (BackpackSummary row : rows) {
            BackpackData data = plugin.repo().loadOrCreate(row.backpackId(), row.backpackType());
            int itemCount = countItems(data);
            int moduleCount = data.installedModules().size();

            Set<Location> locations = plugin.placedBackpacks().getPlacementLocations(row.backpackId());
            boolean placed = !locations.isEmpty();
            String locationText = placed ? formatLocation(locations.iterator().next()) : "";

            out.add(new AdminBackpackListEntry(
                    row.backpackId(),
                    row.backpackType(),
                    data.backpackName(),
                    row.ownerUuid(),
                    row.ownerName(),
                    itemCount,
                    moduleCount,
                    placed,
                    locationText,
                    row.updatedAt()));
        }
        return out;
    }

    private List<AdminBackpackListEntry> sortedEntries(AdminBackpackListMenuHolder holder) {
        List<AdminBackpackListEntry> sorted = new ArrayList<>(holder.entries());
        Comparator<AdminBackpackListEntry> cmp = switch (holder.sortField()) {
            case TYPE -> Comparator.comparing(AdminBackpackListEntry::typeKey)
                    .thenComparing(AdminBackpackListEntry::nameKey)
                    .thenComparing(AdminBackpackListEntry::backpackId);
            case NAME -> Comparator.comparing(AdminBackpackListEntry::nameKey)
                    .thenComparing(AdminBackpackListEntry::typeKey)
                    .thenComparing(AdminBackpackListEntry::backpackId);
            case QUANTITY -> Comparator.comparingInt(AdminBackpackListEntry::itemCount)
                    .thenComparing(AdminBackpackListEntry::typeKey)
                    .thenComparing(AdminBackpackListEntry::backpackId);
            case MODULES -> Comparator.comparingInt(AdminBackpackListEntry::moduleCount)
                    .thenComparing(AdminBackpackListEntry::typeKey)
                    .thenComparing(AdminBackpackListEntry::backpackId);
            case LOCATION -> Comparator.comparing(AdminBackpackListEntry::placed)
                    .thenComparing(AdminBackpackListEntry::locationKey)
                    .thenComparing(AdminBackpackListEntry::backpackId);
            case LAST_ACCESSED -> Comparator.comparingLong(AdminBackpackListEntry::lastAccessedMillis)
                    .thenComparing(AdminBackpackListEntry::backpackId);
        };

        if (!holder.ascending()) {
            cmp = cmp.reversed();
        }

        sorted.sort(cmp);
        return sorted;
    }

    private ItemStack namedItemWithGuiMarker(Material mat, String name, String guiValue) {
        return namedItemWithGuiMarker(mat, name, List.of(), guiValue);
    }

    private ItemStack namedItemWithGuiMarker(Material mat, String name, List<String> lore, String guiValue) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(Text.lore(lore));
            }
            meta.getPersistentDataContainer().set(plugin.cfg().getGuiItemKey(), PersistentDataType.STRING, guiValue);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasGuiValue(ItemStack item, String expected) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String value = meta.getPersistentDataContainer().get(plugin.cfg().getGuiItemKey(), PersistentDataType.STRING);
        return expected.equals(value);
    }

    private static String safeType(String type) {
        return type == null ? "unknown" : type;
    }

    private static int incrementTypeCounter(Map<String, Integer> counts, String key) {
        Integer current = counts.get(key);
        int next = (current == null ? 1 : current + 1);
        counts.put(key, next);
        return next;
    }

    private static int countItems(BackpackData data) {
        if (data == null || data.contentsBytes() == null || data.contentsBytes().length == 0) {
            return 0;
        }

        int total = 0;
        ItemStack[] items = ItemStackCodec.fromBytes(data.contentsBytes());
        for (ItemStack item : items) {
            if (ItemStacks.isNotAir(item)) {
                total += Math.max(0, item.getAmount());
            }
        }
        return total;
    }

    private static String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + " (" + location.getBlockX() + ", " + location.getBlockY() + ", "
                + location.getBlockZ() + ")";
    }

    private static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return "unknown";
        }

        Instant when = Instant.ofEpochMilli(epochMillis);
        String absolute = TS_FORMAT.format(when);

        long seconds = Math.max(0, Duration.between(when, Instant.now()).getSeconds());
        String relative;
        if (seconds < 60) {
            relative = seconds + "s ago";
        } else if (seconds < 3600) {
            relative = (seconds / 60) + "m ago";
        } else if (seconds < 86400) {
            relative = (seconds / 3600) + "h ago";
        } else {
            relative = (seconds / 86400) + "d ago";
        }

        return absolute + " (" + relative + ")";
    }
}
