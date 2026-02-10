package io.github.tootertutor.ModularPacks.gui;

import java.util.List;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;

public final class BackpackMenuHolder implements InventoryHolder {

    private final UUID backpackId;
    private final BackpackTypeDef type;
    private final BackpackData data;

    private int page = 0;

    // computed at open
    private final int logicalSlots; // rows*9
    private final int pageSize; // 45 if paginated, else rows*9
    private final boolean paginated;

    private final List<Integer> upgradeSlots; // actual inventory slot indices (bottom row)

    private BackpackSortMode sortMode = BackpackSortMode.REGISTRY;
    private boolean sortLocked = false;

    private Inventory inventory;

    public BackpackMenuHolder(UUID backpackId, BackpackTypeDef type, BackpackData data, boolean paginated, int pageSize,
            List<Integer> upgradeSlots) {
        this.backpackId = backpackId;
        this.type = type;
        this.data = data;
        this.paginated = paginated;
        this.pageSize = pageSize;
        this.logicalSlots = type.rows() * 9;
        this.upgradeSlots = upgradeSlots;
        this.sortLocked = data.sortLocked();
    }

    public UUID backpackId() {
        return backpackId;
    }

    public BackpackTypeDef type() {
        return type;
    }

    public BackpackData data() {
        return data;
    }

    public boolean paginated() {
        return paginated;
    }

    public int pageSize() {
        return pageSize;
    }

    public int logicalSlots() {
        return logicalSlots;
    }

    public int page() {
        return page;
    }

    public void page(int p) {
        this.page = Math.max(0, p);
    }

    public int pageCount() {
        if (!paginated)
            return 1;
        return (int) Math.ceil(logicalSlots / (double) pageSize);
    }

    public List<Integer> upgradeSlots() {
        return upgradeSlots;
    }

    public BackpackSortMode sortMode() {
        return sortMode;
    }

    public void sortMode(BackpackSortMode mode) {
        if (mode == null)
            mode = BackpackSortMode.REGISTRY;
        this.sortMode = mode;
    }

    public boolean sortLocked() {
        return sortLocked;
    }

    public void sortLocked(boolean locked) {
        this.sortLocked = locked;
    }

    public boolean toggleSortLocked() {
        this.sortLocked = !this.sortLocked;
        return this.sortLocked;
    }

    public void setInventory(Inventory inv) {
        this.inventory = inv;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
