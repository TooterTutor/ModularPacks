package io.github.tootertutor.ModularPacks.gui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository.BackpackSummary;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenuHolder.AdminBackpackListEntry;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenuHolder.InteractionMode;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
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

        Component title = Text.c(plugin.lang().text(viewer, "gui.admin.title", "owner", ownerName));
        Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.setInventory(inv);
        holder.viewer(viewer);

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
            inv.setItem(slot++, buildBackpackEntry(holder.viewer(), row, index));
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
        Player player = holder.viewer();
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) {
            inv.setItem(slot, namedItemWithGuiMarker(plugin.cfg().navBorderFiller(), "&7", "admin-list-filler"));
        }

        int page = holder.page() + 1;
        int pageCount = holder.pageCount();

        List<String> modeLore = List.of(
                plugin.lang().text(player, "gui.admin.mode", "mode",
                        plugin.lang().text(player, "gui.admin.modes." + holder.mode().name().toLowerCase(Locale.ROOT))),
                plugin.lang().text(player, "gui.admin.toggleMode"),
                plugin.lang().text(player, "gui.admin.viewHint"),
                plugin.lang().text(player, "gui.admin.recoverHint"));
        String modeName = holder.mode() == InteractionMode.VIEW ? plugin.lang().text(player, "gui.admin.view")
                : plugin.lang().text(player, "gui.admin.recover");
        inv.setItem(NAV_MODE_SLOT, namedItemWithGuiMarker(Material.COMPARATOR, modeName, modeLore, GUI_MODE));

        List<String> sortLore = new ArrayList<>();
        sortLore.add(plugin.lang().text(player, "gui.admin.changeField"));
        sortLore.add(plugin.lang().text(player, "gui.admin.toggleDirection"));
        sortLore.add(plugin.lang().text(player, "gui.admin.direction", "direction",
                plugin.lang().text(player, holder.ascending() ? "gui.admin.ascending" : "gui.admin.descending")));
        sortLore.add("&7");
        sortLore.add(plugin.lang().text(player, "gui.admin.field"));
        for (AdminBackpackListMenuHolder.SortField field : AdminBackpackListMenuHolder.SortField.values()) {
            String color = (field == holder.sortField()) ? "&a" : "&7";
            sortLore.add(color + plugin.lang().get(player,
                    "gui.admin.fields." + field.name().toLowerCase(java.util.Locale.ROOT), field.label()));
        }
        inv.setItem(NAV_SORT_SLOT, namedItemWithGuiMarker(Material.HOPPER, plugin.lang().text(player, "gui.admin.sort"),
                sortLore, GUI_SORT));

        if (holder.page() > 0) {
            List<String> lore = List.of(
                    plugin.lang().text(player, "gui.admin.page", "page", page, "pages", pageCount),
                    plugin.lang().text(player, "gui.admin.previousHint"));
            inv.setItem(NAV_PREV_SLOT,
                    namedItemWithGuiMarker(plugin.cfg().navPageButtons(),
                            plugin.lang().text(player, "gui.admin.previous"), lore, GUI_PREV));
        }

        if (holder.page() + 1 < pageCount) {
            List<String> lore = List.of(
                    plugin.lang().text(player, "gui.admin.page", "page", page, "pages", pageCount),
                    plugin.lang().text(player, "gui.admin.nextHint"));
            inv.setItem(NAV_NEXT_SLOT,
                    namedItemWithGuiMarker(plugin.cfg().navPageButtons(), plugin.lang().text(player, "gui.admin.next"),
                            lore, GUI_NEXT));
        }
    }

    private ItemStack buildBackpackEntry(Player player, AdminBackpackListEntry row, int index) {
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

        List<String> lore = plugin.lang().lines(player, "gui.admin.entryLore",
                "type", safeType(row.backpackType()),
                "name", customName.isEmpty() ? plugin.lang().text(player, "gui.admin.defaultName") : customName,
                "items", row.itemCount(), "modules", row.moduleCount(),
                "location", row.placed() ? row.locationText() : plugin.lang().text(player, "gui.admin.notPlaced"),
                "accessed", formatTimestamp(player, row.lastAccessedMillis()), "id", row.backpackId());

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
            long itemCount = countItems(data);
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
            case TYPE -> Comparator.comparingInt((AdminBackpackListEntry e) -> typeTierRank(e.backpackType()))
                    .thenComparing(e -> e.typeKey())
                    .thenComparing(e -> e.nameKey())
                    .thenComparing(e -> e.backpackId());
            case NAME -> Comparator.comparing((AdminBackpackListEntry e) -> e.nameKey())
                    .thenComparing(e -> e.typeKey())
                    .thenComparing(e -> e.backpackId());
            case QUANTITY -> Comparator.comparingLong((AdminBackpackListEntry e) -> e.itemCount())
                    .thenComparing(e -> e.typeKey())
                    .thenComparing(e -> e.backpackId());
            case MODULES -> Comparator.comparingInt((AdminBackpackListEntry e) -> e.moduleCount())
                    .thenComparing(e -> e.typeKey())
                    .thenComparing(e -> e.backpackId());
            case LOCATION -> Comparator.comparing((AdminBackpackListEntry e) -> e.placed())
                    .thenComparing(e -> e.locationKey())
                    .thenComparing(e -> e.backpackId());
            case LAST_ACCESSED -> Comparator.comparingLong((AdminBackpackListEntry e) -> e.lastAccessedMillis())
                    .thenComparing(e -> e.backpackId());
        };

        if (!holder.ascending()) {
            cmp = cmp.reversed();
        }

        sorted.sort(cmp);
        return sorted;
    }

    private int typeTierRank(String typeId) {
        BackpackTypeDef def = plugin.cfg().findType(typeId);
        if (def == null) {
            return Integer.MAX_VALUE;
        }

        if (def.customModelData() > 0) {
            return def.customModelData();
        }

        return (def.rows() * 100) + def.upgradeSlots();
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

    private long countItems(BackpackData data) {
        if (data == null) {
            return 0;
        }
        return plugin.backpackStorage().load(data).totalCount();
    }

    private static String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + " (" + location.getBlockX() + ", " + location.getBlockY() + ", "
                + location.getBlockZ() + ")";
    }

    private String formatTimestamp(Player player, long epochMillis) {
        if (epochMillis <= 0) {
            return plugin.lang().text(player, "gui.admin.unknown");
        }

        Instant when = Instant.ofEpochMilli(epochMillis);
        String absolute = TS_FORMAT.format(when);

        long seconds = Math.max(0, Duration.between(when, Instant.now()).getSeconds());
        String relative;
        if (seconds < 60) {
            relative = plugin.lang().text(player, "gui.admin.secondsAgo", "count", seconds);
        } else if (seconds < 3600) {
            relative = plugin.lang().text(player, "gui.admin.minutesAgo", "count", seconds / 60);
        } else if (seconds < 86400) {
            relative = plugin.lang().text(player, "gui.admin.hoursAgo", "count", seconds / 3600);
        } else {
            relative = plugin.lang().text(player, "gui.admin.daysAgo", "count", seconds / 86400);
        }

        return absolute + " (" + relative + ")";
    }
}
