package io.github.tootertutor.ModularPacks.storage;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

/**
 * An item identity prototype and its independent logical quantity.
 */
public final class StoredStack {

    private final ItemStack prototype;
    private final long count;

    public StoredStack(ItemStack prototype, long count) {
        if (prototype == null || prototype.getType().isAir()) {
            throw new IllegalArgumentException("prototype must be a non-air item");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be greater than zero");
        }

        this.prototype = prototype.clone();
        this.prototype.setAmount(1);
        this.count = count;
    }

    /**
     * Returns a defensive clone normalized to an amount of one.
     */
    public ItemStack prototype() {
        return prototype.clone();
    }

    public long count() {
        return count;
    }

    public StoredStack withCount(long newCount) {
        return new StoredStack(prototype, newCount);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoredStack that)) {
            return false;
        }
        return count == that.count && prototype.equals(that.prototype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prototype, count);
    }

    @Override
    public String toString() {
        return "StoredStack[prototype=" + prototype + ", count=" + count + "]";
    }
}
