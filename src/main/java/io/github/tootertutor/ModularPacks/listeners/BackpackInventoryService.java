package io.github.tootertutor.ModularPacks.listeners;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.gui.BackpackSortMode;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

/**
 * Handles backpack inventory operations: inserting items, sorting, and merging
 * partial stacks.
 */
public final class BackpackInventoryService {

    private final ModularPacksPlugin plugin;

    public BackpackInventoryService(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack insertIntoBackpackLogical(BackpackMenuHolder holder, ItemStack stack) {
        if (ItemStacks.isAir(stack))
            return stack;
        if (!plugin.cfg().isAllowedInBackpack(stack))
            return stack;

        ItemStack[] logical = ItemStackCodec.fromBytes(holder.data().contentsBytes());
        int logicalSize = holder.logicalSlots();

        if (logical.length != logicalSize) {
            ItemStack[] resized = new ItemStack[logicalSize];
            System.arraycopy(logical, 0, resized, 0, Math.min(logical.length, logicalSize));
            logical = resized;
        }

        // Prefer inserting into the CURRENT page range first (prevents client-side
        // sorting mods from using shift-click to accidentally rewrite earlier pages).
        if (holder.paginated()) {
            int pageStart = holder.page() * 45;
            int pageEnd = Math.min(pageStart + 45, logical.length);
            ItemStack remainder = insertIntoLogicalRange(logical, pageStart, pageEnd, stack);
            if (ItemStacks.isAir(remainder) || remainder.getAmount() <= 0) {
                holder.data().contentsBytes(ItemStackCodec.toBytes(logical));
                return null;
            }
            stack = remainder;
        }

        // Fallback: insert anywhere (vanilla-ish behavior if current page is full)
        stack = insertIntoLogicalRange(logical, 0, logical.length, stack);

        holder.data().contentsBytes(ItemStackCodec.toBytes(logical));
        return stack;
    }

    public void sortBackpack(BackpackMenuHolder holder, BackpackMenuRenderer renderer) {
        // Ensure the current visible page is merged into the logical contents first.
        renderer.saveVisibleStorageToData(holder);

        int logicalSize = holder.logicalSlots();
        ItemStack[] logical = ItemStackCodec.fromBytes(holder.data().contentsBytes());

        if (logical.length != logicalSize) {
            ItemStack[] resized = new ItemStack[logicalSize];
            System.arraycopy(logical, 0, resized, 0, Math.min(logical.length, logicalSize));
            logical = resized;
        }

        List<ItemStack> items = new ArrayList<>(logical.length);
        for (ItemStack it : logical) {
            if (ItemStacks.isNotAir(it))
                items.add(it.clone());
        }

        // Merge partial stacks BEFORE sorting (so COUNT sort and other comparators
        // behave predictably and we don't leave unnecessary partials).
        items = mergePartialStacks(items);

        items.sort(BackpackSortMode.comparator(plugin, holder.sortMode()));

        ItemStack[] out = new ItemStack[logicalSize];
        for (int i = 0; i < Math.min(out.length, items.size()); i++) {
            out[i] = items.get(i);
        }

        holder.data().contentsBytes(ItemStackCodec.toBytes(out));
    }

    private ItemStack insertIntoLogicalRange(ItemStack[] logical, int start, int end, ItemStack stack) {
        if (ItemStacks.isAir(stack))
            return stack;
        start = Math.max(0, start);
        end = Math.max(start, Math.min(end, logical.length));

        // merge
        for (int i = start; i < end; i++) {
            ItemStack slot = logical[i];
            if (ItemStacks.isAir(slot))
                continue;

            if (!slot.isSimilar(stack))
                continue;

            int maxStack = slot.getMaxStackSize();
            int current = slot.getAmount();
            int space = maxStack - current;
            if (space <= 0)
                continue;

            int toMove = Math.min(space, stack.getAmount());
            slot.setAmount(current + toMove);
            stack.setAmount(stack.getAmount() - toMove);

            if (stack.getAmount() <= 0)
                return null;
        }

        // empty slots
        for (int i = start; i < end; i++) {
            if (ItemStacks.isNotAir(logical[i]))
                continue;

            logical[i] = stack.clone();
            return null;
        }

        return stack;
    }

    private static List<ItemStack> mergePartialStacks(List<ItemStack> input) {
        if (input == null || input.isEmpty())
            return input;

        ArrayList<ItemStack> merged = new ArrayList<>(input.size());

        for (ItemStack stack : input) {
            if (ItemStacks.isAir(stack))
                continue;

            boolean didMerge = false;

            for (int i = 0; i < merged.size(); i++) {
                ItemStack existing = merged.get(i);
                if (existing == null)
                    continue;

                if (!existing.isSimilar(stack))
                    continue;

                int maxStack = existing.getMaxStackSize();
                int current = existing.getAmount();
                int incoming = stack.getAmount();
                int total = current + incoming;

                if (total <= maxStack) {
                    existing.setAmount(total);
                    didMerge = true;
                    break;
                } else {
                    int toMove = maxStack - current;
                    existing.setAmount(maxStack);
                    stack.setAmount(incoming - toMove);
                }
            }

            if (!didMerge) {
                merged.add(stack.clone());
            }
        }

        return merged;
    }
}
