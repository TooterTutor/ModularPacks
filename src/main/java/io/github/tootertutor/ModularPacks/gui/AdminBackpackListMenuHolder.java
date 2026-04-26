package io.github.tootertutor.ModularPacks.gui;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AdminBackpackListMenuHolder implements InventoryHolder {

    private static final int PAGE_SIZE = 45;

    private final UUID ownerUuid;
    private final String ownerName;
    private final List<AdminBackpackListEntry> entries;

    private int page;
    private SortField sortField = SortField.TYPE;
    private boolean ascending = true;
    private InteractionMode mode = InteractionMode.VIEW;
    private Inventory inventory;

    public AdminBackpackListMenuHolder(UUID ownerUuid, String ownerName, List<AdminBackpackListEntry> entries,
            int page) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.page = Math.max(0, page);
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public List<AdminBackpackListEntry> entries() {
        return entries;
    }

    public AdminBackpackListEntry findEntry(UUID backpackId) {
        if (backpackId == null) {
            return null;
        }
        for (AdminBackpackListEntry entry : entries) {
            if (entry.backpackId().equals(backpackId)) {
                return entry;
            }
        }
        return null;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    public int pageSize() {
        return PAGE_SIZE;
    }

    public int pageCount() {
        return Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
    }

    public SortField sortField() {
        return sortField;
    }

    public void cycleSortField() {
        SortField[] fields = SortField.values();
        int next = (sortField.ordinal() + 1) % fields.length;
        sortField = fields[next];
        this.page = 0;
    }

    public boolean ascending() {
        return ascending;
    }

    public void toggleSortDirection() {
        this.ascending = !this.ascending;
        this.page = 0;
    }

    public InteractionMode mode() {
        return mode;
    }

    public void toggleMode() {
        this.mode = (this.mode == InteractionMode.VIEW) ? InteractionMode.RECOVER : InteractionMode.VIEW;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public enum SortField {
        TYPE("Type"),
        NAME("Name"),
        QUANTITY("Quantity"),
        MODULES("Current Modules"),
        LOCATION("Location"),
        LAST_ACCESSED("Last Accessed");

        private final String label;

        SortField(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum InteractionMode {
        VIEW,
        RECOVER
    }

    public record AdminBackpackListEntry(
            UUID backpackId,
            String backpackType,
            String backpackName,
            String ownerUuid,
            String ownerName,
            int itemCount,
            int moduleCount,
            boolean placed,
            String locationText,
            long lastAccessedMillis) {

        public String typeKey() {
            return backpackType == null ? "unknown" : backpackType.toLowerCase(Locale.ROOT);
        }

        public String nameKey() {
            return backpackName == null ? "" : backpackName.toLowerCase(Locale.ROOT);
        }

        public String locationKey() {
            return locationText == null ? "" : locationText.toLowerCase(Locale.ROOT);
        }
    }
}
