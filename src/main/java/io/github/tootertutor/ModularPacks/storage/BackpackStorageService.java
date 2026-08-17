package io.github.tootertutor.ModularPacks.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

/**
 * Canonical load/save and mutation boundary for logical backpack contents.
 */
public final class BackpackStorageService {

    private final BackpackStorageCodec codec;
    private final StackIdentityService identity;
    private final StackCapacityProvider capacityService;

    public BackpackStorageService() {
        this(new BackpackStorageCodec(), new StackIdentityService(), null);
    }

    public BackpackStorageService(StackCapacityProvider capacityService) {
        this(new BackpackStorageCodec(), new StackIdentityService(), capacityService);
    }

    public BackpackStorageService(BackpackStorageCodec codec, StackIdentityService identity) {
        this(codec, identity, null);
    }

    public BackpackStorageService(BackpackStorageCodec codec, StackIdentityService identity,
            StackCapacityProvider capacityService) {
        if (codec == null || identity == null) {
            throw new IllegalArgumentException("codec and identity cannot be null");
        }
        this.codec = codec;
        this.identity = identity;
        this.capacityService = capacityService;
    }

    public BackpackStorage load(BackpackData data) {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }

        byte[] bytes = data.contentsBytes();
        if (bytes == null || bytes.length == 0) {
            return new BackpackStorage(0);
        }
        if (codec.isEncodedStorage(bytes)) {
            return codec.decode(bytes);
        }

        ItemStack[] legacy = ItemStackCodec.fromBytes(bytes);
        return BackpackStorage.fromVanillaContents(legacy);
    }

    public void save(BackpackData data, BackpackStorage storage) {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }
        data.contentsBytes(codec.encode(storage));
    }

    /**
     * Copies the opaque encoded payload for session/repository synchronization.
     * Callers must not interpret the bytes as an item array.
     */
    public void copyEncodedContents(BackpackData source, BackpackData target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("source and target cannot be null");
        }
        byte[] bytes = source.contentsBytes();
        if (bytes != null) {
            target.contentsBytes(bytes.clone());
        }
    }

    public StoredStack get(BackpackStorage storage, int slot) {
        requireStorage(storage);
        return storage.get(slot);
    }

    public BackpackStorage load(BackpackData data, int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be non-negative");
        }
        return load(data).resized(expectedSize);
    }

    /** Vanilla materialization capacity for a real Bukkit stack. */
    public long capacityFor(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException("item must be non-air");
        }
        return item.getMaxStackSize();
    }

    public long capacityFor(StoredStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("stack cannot be null");
        }
        return capacityFor(stack.prototype());
    }

    public long capacityFor(BackpackData data, ItemStack item) {
        return capacityFor(data, item, null);
    }

    public long capacityFor(BackpackData data, ItemStack item, java.util.UUID excludedModuleId) {
        return capacityService == null
                ? capacityFor(item)
                : capacityService.capacityFor(data, item, excludedModuleId);
    }

    /**
     * Inserts up to {@code count} logical items and returns the inserted amount.
     */
    public long insert(BackpackStorage storage, ItemStack prototype, long count) {
        return insert(storage, 0, storage == null ? 0 : storage.size(), prototype, count);
    }

    public long insert(BackpackData data, BackpackStorage storage, ItemStack prototype, long count) {
        return insert(data, storage, 0, storage == null ? 0 : storage.size(), prototype, count);
    }

    /**
     * Inserts the amount carried by {@code stack}; returns the amount inserted.
     */
    public long insert(BackpackStorage storage, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        return insert(storage, stack, stack.getAmount());
    }

    public long insert(BackpackData data, BackpackStorage storage, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        return insert(data, storage, stack, stack.getAmount());
    }

    /**
     * Inserts into {@code [startInclusive, endExclusive)} and returns the amount
     * inserted. Existing matching stacks are filled before empty slots.
     */
    public long insert(BackpackStorage storage, int startInclusive, int endExclusive,
            ItemStack prototype, long count) {
        return insertAtCapacity(storage, startInclusive, endExclusive, prototype, count, capacityFor(prototype));
    }

    public long insert(BackpackData data, BackpackStorage storage, int startInclusive, int endExclusive,
            ItemStack prototype, long count) {
        return insertAtCapacity(storage, startInclusive, endExclusive, prototype, count,
                capacityFor(data, prototype));
    }

    private long insertAtCapacity(BackpackStorage storage, int startInclusive, int endExclusive,
            ItemStack prototype, long count, long capacity) {
        requireStorage(storage);
        if (prototype == null || prototype.getType().isAir()) {
            throw new IllegalArgumentException("prototype must be non-air");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        if (count == 0) {
            return 0;
        }

        int start = checkedRangeStart(storage, startInclusive);
        int end = checkedRangeEnd(storage, start, endExclusive);

        long remaining = count;
        long inserted = 0;
        for (int i = start; i < end && remaining > 0; i++) {
            StoredStack current = storage.get(i);
            if (current == null || !identity.sameIdentity(current, prototype) || current.count() >= capacity) {
                continue;
            }
            long moved = Math.min(capacity - current.count(), remaining);
            storage.set(i, current.withCount(current.count() + moved));
            remaining -= moved;
            inserted += moved;
        }

        for (int i = start; i < end && remaining > 0; i++) {
            if (storage.get(i) != null) {
                continue;
            }
            long moved = Math.min(capacity, remaining);
            storage.set(i, new StoredStack(prototype, moved));
            remaining -= moved;
            inserted += moved;
        }

        return inserted;
    }

    /**
     * Inserts into one logical slot and returns the amount inserted.
     */
    public long insertIntoSlot(BackpackStorage storage, int slot, ItemStack prototype, long count) {
        return insert(storage, slot, slot + 1, prototype, count);
    }

    public long insertIntoSlot(BackpackData data, BackpackStorage storage, int slot,
            ItemStack prototype, long count) {
        return insert(data, storage, slot, slot + 1, prototype, count);
    }

    public boolean canAccept(BackpackStorage storage, ItemStack prototype) {
        return availableCapacity(storage, prototype) > 0;
    }

    public boolean canAccept(BackpackStorage storage, ItemStack prototype, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        return count == 0 || availableCapacity(storage, prototype) >= count;
    }

    public boolean canAccept(BackpackData data, BackpackStorage storage, ItemStack prototype, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        return count == 0 || availableCapacity(data, storage, prototype) >= count;
    }

    public long availableCapacity(BackpackStorage storage, ItemStack prototype) {
        return availableCapacityAt(storage, prototype, capacityFor(prototype));
    }

    public long availableCapacity(BackpackData data, BackpackStorage storage, ItemStack prototype) {
        return availableCapacityAt(storage, prototype, capacityFor(data, prototype));
    }

    private long availableCapacityAt(BackpackStorage storage, ItemStack prototype, long capacity) {
        requireStorage(storage);
        if (prototype == null || prototype.getType().isAir()) {
            throw new IllegalArgumentException("prototype must be non-air");
        }

        long available = 0;
        for (int i = 0; i < storage.size(); i++) {
            StoredStack current = storage.get(i);
            if (current == null) {
                available = addSaturated(available, capacity);
            } else if (identity.sameIdentity(current, prototype) && current.count() < capacity) {
                available = addSaturated(available, capacity - current.count());
            }
        }
        return available;
    }

    /**
     * Consolidates equal identities at current capacity, sorts the resulting
     * logical stacks, and returns a new storage with empty slots at the end.
     */
    public BackpackStorage compactAndSort(BackpackStorage storage, Comparator<StoredStack> comparator) {
        return compactAndSort(null, storage, comparator);
    }

    public BackpackStorage compactAndSort(BackpackData data, BackpackStorage storage,
            Comparator<StoredStack> comparator) {
        requireStorage(storage);
        if (comparator == null) {
            throw new IllegalArgumentException("comparator cannot be null");
        }

        BackpackStorage consolidated = new BackpackStorage(storage.size());
        for (int i = 0; i < storage.size(); i++) {
            StoredStack stack = storage.get(i);
            if (stack == null) {
                continue;
            }
            long inserted = data == null
                    ? insert(consolidated, stack.prototype(), stack.count())
                    : insert(data, consolidated, stack.prototype(), stack.count());
            if (inserted != stack.count()) {
                throw new IllegalStateException(
                        "Compaction cannot represent all logical items at current capacity");
            }
        }

        List<StoredStack> stacks = new ArrayList<>(consolidated.usedSlots());
        for (int i = 0; i < consolidated.size(); i++) {
            StoredStack stack = consolidated.get(i);
            if (stack != null) {
                stacks.add(stack);
            }
        }
        stacks.sort(comparator);

        BackpackStorage sorted = new BackpackStorage(storage.size());
        for (int i = 0; i < stacks.size(); i++) {
            sorted.set(i, stacks.get(i));
        }
        return sorted;
    }

    /**
     * Extracts matching logical items and returns the extracted amount.
     */
    public long extract(BackpackStorage storage, ItemStack prototype, long count) {
        return extractMatching(storage, prototype, count);
    }

    /**
     * Extracts matching logical items and returns the amount extracted.
     */
    public long extractMatching(BackpackStorage storage, ItemStack prototype, long count) {
        requireStorage(storage);
        if (prototype == null || prototype.getType().isAir()) {
            throw new IllegalArgumentException("prototype must be non-air");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }

        long remaining = count;
        long extracted = 0;
        for (int i = 0; i < storage.size() && remaining > 0; i++) {
            StoredStack current = storage.get(i);
            if (current == null || !identity.sameIdentity(current, prototype)) {
                continue;
            }
            long moved = Math.min(current.count(), remaining);
            long newCount = current.count() - moved;
            if (newCount == 0) {
                storage.clear(i);
            } else {
                storage.set(i, current.withCount(newCount));
            }
            remaining -= moved;
            extracted += moved;
        }
        return extracted;
    }

    public long countMatching(BackpackStorage storage, ItemStack prototype) {
        requireStorage(storage);
        if (prototype == null || prototype.getType().isAir()) {
            throw new IllegalArgumentException("prototype must be non-air");
        }

        long total = 0;
        for (int i = 0; i < storage.size(); i++) {
            StoredStack current = storage.get(i);
            if (current != null && identity.sameIdentity(current, prototype)) {
                total = Math.addExact(total, current.count());
            }
        }
        return total;
    }

    /**
     * Removes up to one legal Bukkit stack from a logical slot.
     */
    public ItemStack extractFromSlot(BackpackStorage storage, int slot, int requestedAmount) {
        requireStorage(storage);
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("requestedAmount must be greater than zero");
        }

        StoredStack current = storage.get(slot);
        if (current == null) {
            return null;
        }

        int amount = Math.toIntExact(Math.min(
                Math.min(current.count(), requestedAmount),
                capacityFor(current)));
        ItemStack extracted = materialize(current, amount);
        long remaining = current.count() - amount;
        if (remaining == 0) {
            storage.clear(slot);
        } else {
            storage.set(slot, current.withCount(remaining));
        }
        return extracted;
    }

    /**
     * Removes matching items and materializes at most one legal Bukkit stack.
     */
    public ItemStack extractMatchingItem(BackpackStorage storage, ItemStack prototype, int requestedAmount) {
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("requestedAmount must be greater than zero");
        }
        int legalAmount = Math.toIntExact(Math.min(requestedAmount, capacityFor(prototype)));
        long extracted = extractMatching(storage, prototype, legalAmount);
        if (extracted == 0) {
            return null;
        }
        return materialize(prototype, Math.toIntExact(extracted));
    }

    /**
     * Creates a real Bukkit stack without modifying the stored prototype.
     */
    public ItemStack materialize(StoredStack stored, int amount) {
        if (stored == null) {
            throw new IllegalArgumentException("stored cannot be null");
        }
        if (amount > stored.count()) {
            throw new IllegalArgumentException("amount exceeds logical count");
        }
        return materialize(stored.prototype(), amount);
    }

    public ItemStack materialize(ItemStack prototype, int amount) {
        if (prototype == null || prototype.getType().isAir()) {
            throw new IllegalArgumentException("prototype must be non-air");
        }
        long capacity = capacityFor(prototype);
        if (amount <= 0 || amount > capacity) {
            throw new IllegalArgumentException("amount must be between 1 and " + capacity);
        }
        ItemStack materialized = prototype.clone();
        materialized.setAmount(amount);
        return materialized;
    }

    /**
     * Replaces one logical slot from an observed vanilla inventory slot.
     */
    public void replaceFromMaterialized(BackpackStorage storage, int slot, ItemStack item) {
        requireStorage(storage);
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            storage.clear(slot);
            return;
        }
        if (item.getAmount() > capacityFor(item)) {
            throw new IllegalArgumentException("materialized item exceeds vanilla capacity");
        }
        storage.set(slot, new StoredStack(item, item.getAmount()));
    }

    /**
     * Compatibility projection for legacy/public array-based consumers. This method
     * fails instead of truncating if any logical count exceeds vanilla.
     */
    @Deprecated(forRemoval = false)
    public ItemStack[] loadVanillaContents(BackpackData data, int expectedSize) {
        return load(data).resized(expectedSize).toVanillaContents();
    }

    @Deprecated(forRemoval = false)
    public ItemStack[] loadVanillaContents(BackpackData data) {
        return load(data).toVanillaContents();
    }

    /**
     * Compatibility adapter for legacy/public integrations. Internal gameplay code
     * must mutate {@link BackpackStorage} directly.
     */
    @Deprecated(forRemoval = false)
    public void saveVanillaContents(BackpackData data, ItemStack[] contents) {
        save(data, BackpackStorage.fromVanillaContents(contents));
    }

    public BackpackStorageCodec codec() {
        return codec;
    }

    public StackIdentityService identity() {
        return identity;
    }

    public StackCapacityProvider capacityService() {
        return capacityService;
    }

    private static void requireStorage(BackpackStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("storage cannot be null");
        }
    }

    private static int checkedRangeStart(BackpackStorage storage, int start) {
        if (start < 0 || start > storage.size()) {
            throw new IndexOutOfBoundsException("start " + start + " outside storage size " + storage.size());
        }
        return start;
    }

    private static int checkedRangeEnd(BackpackStorage storage, int start, int end) {
        if (end < start || end > storage.size()) {
            throw new IndexOutOfBoundsException("end " + end + " outside storage size " + storage.size());
        }
        return end;
    }

    private static long addSaturated(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
