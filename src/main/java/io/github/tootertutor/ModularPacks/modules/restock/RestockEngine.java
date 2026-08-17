package io.github.tootertutor.ModularPacks.modules.restock;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class RestockEngine {

    private static final int DEFAULT_THRESHOLD = 16;

    private final ModularPacksPlugin plugin;

    public RestockEngine(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean applyRestock(Player player, BackpackStorage backpackContents, int threshold,
            List<ItemStack> whitelist) {
        if (player == null || backpackContents == null)
            return false;
        threshold = clampThreshold(threshold);
        boolean hasWhitelist = whitelist != null && !whitelist.isEmpty();

        boolean changed = false;

        // Hotbar first (0..8), then main inventory (9..35). Skip armor/offhand.
        var inv = player.getInventory();

        changed |= restockRange(player, inv, backpackContents, threshold, whitelist, hasWhitelist, 0, 9);
        changed |= restockRange(player, inv, backpackContents, threshold, whitelist, hasWhitelist, 9, 36);

        return changed;
    }

    public static int clampThreshold(int threshold) {
        if (threshold <= 0)
            return DEFAULT_THRESHOLD;
        return Math.max(1, Math.min(64, threshold));
    }

    private boolean restockRange(
            Player player,
            org.bukkit.inventory.PlayerInventory inv,
            BackpackStorage backpackContents,
            int threshold,
            List<ItemStack> whitelist,
            boolean hasWhitelist,
            int startInclusive,
            int endExclusive) {
        boolean changed = false;
        for (int slot = startInclusive; slot < endExclusive; slot++) {
            if (plugin.quiver() != null && plugin.quiver().isProxySlot(player, slot))
                continue;
            ItemStack stack = inv.getItem(slot);
            if (ItemStacks.isAir(stack))
                continue;

            // Never restock backpacks/modules themselves.
            if (hasBlockedPdc(stack))
                continue;

            if (hasWhitelist && !matchesWhitelist(stack, whitelist))
                continue;

            // Only restock stackable items.
            int max = Math.toIntExact(plugin.backpackStorage().capacityFor(stack));
            if (max <= 1)
                continue;

            int amt = stack.getAmount();
            if (amt <= 0 || amt > threshold)
                continue;
            if (amt >= max)
                continue;

            int need = max - amt;
            if (need <= 0)
                continue;

            ItemStack updated = stack.clone();
            long move = Math.min(need, plugin.backpackStorage().countMatching(backpackContents, updated));
            long extracted = plugin.backpackStorage().extractMatching(backpackContents, updated, move);
            if (extracted > 0) {
                updated.setAmount(Math.addExact(updated.getAmount(), Math.toIntExact(extracted)));
                changed = true;
            }

            if (extracted > 0) {
                inv.setItem(slot, updated);
            }
        }
        return changed;
    }

    private boolean matchesWhitelist(ItemStack stack, List<ItemStack> whitelist) {
        if (ItemStacks.isAir(stack) || whitelist == null || whitelist.isEmpty())
            return false;
        for (ItemStack allowed : whitelist) {
            if (ItemStacks.isAir(allowed))
                continue;
            if (plugin.backpackStorage().identity().sameIdentity(allowed, stack))
                return true;
        }
        return false;
    }

    private boolean hasBlockedPdc(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta())
            return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null)
            return false;
        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(keys.BACKPACK_ID, PersistentDataType.STRING)
                || pdc.has(keys.BACKPACK_TYPE, PersistentDataType.STRING)
                || pdc.has(keys.MODULE_TYPE, PersistentDataType.STRING)
                || pdc.has(keys.MODULE_ID, PersistentDataType.STRING);
    }
}
