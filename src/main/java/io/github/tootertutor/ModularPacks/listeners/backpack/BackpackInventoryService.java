package io.github.tootertutor.ModularPacks.listeners.backpack;

import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.gui.BackpackSortMode;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.storage.BackpackStorageService;
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

        BackpackStorageService storageService = plugin.backpackStorage();
        BackpackStorage storage = storageService.load(holder.data(), holder.logicalSlots());
        long remaining = stack.getAmount();

        // Prefer inserting into the CURRENT page range first (prevents client-side
        // sorting mods from using shift-click to accidentally rewrite earlier pages).
        if (holder.paginated()) {
            int pageStart = holder.page() * 45;
            int pageEnd = Math.min(pageStart + 45, storage.size());
            remaining -= storageService.insert(holder.data(), storage, pageStart, pageEnd, stack, remaining);
            if (remaining == 0) {
                storageService.save(holder.data(), storage);
                return null;
            }
        }

        // Fallback: insert anywhere (vanilla-ish behavior if current page is full)
        remaining -= storageService.insert(holder.data(), storage, stack, remaining);

        storageService.save(holder.data(), storage);
        return remaining == 0 ? null : storageService.materialize(stack, Math.toIntExact(remaining));
    }

    public void sortBackpack(BackpackMenuHolder holder, BackpackMenuRenderer renderer) {
        // Ensure the current visible page is merged into the logical contents first.
        renderer.saveVisibleStorageToData(holder);

        BackpackStorageService storageService = plugin.backpackStorage();
        BackpackStorage logical = storageService.load(holder.data(), holder.logicalSlots());
        BackpackStorage sorted = storageService.compactAndSort(
                holder.data(), logical, BackpackSortMode.storedComparator(plugin, holder.sortMode()));

        storageService.save(holder.data(), sorted);
    }
}
