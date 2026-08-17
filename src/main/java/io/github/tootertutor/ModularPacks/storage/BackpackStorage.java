package io.github.tootertutor.ModularPacks.storage;

import org.bukkit.inventory.ItemStack;

/**
 * Encapsulated logical backpack slots. Empty slots are represented by null.
 */
public final class BackpackStorage {

    private final StoredStack[] slots;

    public BackpackStorage(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        this.slots = new StoredStack[size];
    }

    private BackpackStorage(StoredStack[] slots) {
        this.slots = slots.clone();
    }

    public static BackpackStorage fromVanillaContents(ItemStack[] contents) {
        ItemStack[] source = contents == null ? new ItemStack[0] : contents;
        BackpackStorage storage = new BackpackStorage(source.length);
        for (int i = 0; i < source.length; i++) {
            ItemStack item = source[i];
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            storage.slots[i] = new StoredStack(item, item.getAmount());
        }
        return storage;
    }

    public int size() {
        return slots.length;
    }

    public StoredStack get(int slot) {
        checkSlot(slot);
        return slots[slot];
    }

    public void set(int slot, StoredStack stack) {
        checkSlot(slot);
        slots[slot] = stack;
    }

    public void clear(int slot) {
        set(slot, null);
    }

    /**
     * Removes and returns the logical stack in {@code slot}.
     */
    public StoredStack remove(int slot) {
        checkSlot(slot);
        StoredStack removed = slots[slot];
        slots[slot] = null;
        return removed;
    }

    public BackpackStorage copy() {
        return new BackpackStorage(slots);
    }

    public BackpackStorage resized(int newSize) {
        if (newSize < 0) {
            throw new IllegalArgumentException("newSize must be non-negative");
        }
        StoredStack[] resized = new StoredStack[newSize];
        System.arraycopy(slots, 0, resized, 0, Math.min(slots.length, newSize));
        return new BackpackStorage(resized);
    }

    public int usedSlots() {
        int used = 0;
        for (StoredStack slot : slots) {
            if (slot != null) {
                used++;
            }
        }
        return used;
    }

    public long totalCount() {
        long total = 0;
        for (StoredStack slot : slots) {
            if (slot != null) {
                total = Math.addExact(total, slot.count());
            }
        }
        return total;
    }

    /**
     * Temporary Phase 2 projection used by legacy gameplay consumers.
     *
     * @throws IllegalStateException if a logical count cannot be represented as a
     *                               vanilla ItemStack without truncation
     */
    public ItemStack[] toVanillaContents() {
        ItemStack[] contents = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            StoredStack stored = slots[i];
            if (stored == null) {
                continue;
            }

            ItemStack item = stored.prototype();
            long maximum = item.getMaxStackSize();
            if (stored.count() > maximum || stored.count() > Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Logical stack in slot " + i + " has count " + stored.count()
                                + " but vanilla capacity is " + maximum);
            }
            item.setAmount(Math.toIntExact(stored.count()));
            contents[i] = item;
        }
        return contents;
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= slots.length) {
            throw new IndexOutOfBoundsException("slot " + slot + " outside storage size " + slots.length);
        }
    }
}
