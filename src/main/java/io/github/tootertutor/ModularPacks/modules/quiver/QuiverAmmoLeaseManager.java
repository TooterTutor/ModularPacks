package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.storage.StackIdentityService;

/** Owns temporary proxy arrows and removes only the leased unit. */
public final class QuiverAmmoLeaseManager {

    private final NamespacedKey markerKey;
    private final StackIdentityService identity;
    private final Map<UUID, QuiverAmmoLease> active = new HashMap<>();

    public QuiverAmmoLeaseManager(NamespacedKey markerKey, StackIdentityService identity) {
        if (markerKey == null || identity == null) {
            throw new IllegalArgumentException("markerKey and identity cannot be null");
        }
        this.markerKey = markerKey;
        this.identity = identity;
    }

    public QuiverAmmoLease stage(Player player, QuiverSource source, int inventorySlot,
            Material weaponType, EquipmentSlot hand, int currentTick) {
        if (player == null || source == null || hand == null
                || (weaponType != Material.BOW && weaponType != Material.CROSSBOW)) {
            throw new IllegalArgumentException("invalid Quiver lease");
        }
        PlayerInventory inventory = player.getInventory();
        if (inventorySlot < 0 || inventorySlot >= 36 || !isEmpty(inventory.getItem(inventorySlot))) {
            return null;
        }
        cleanup(player, null);

        UUID leaseId = UUID.randomUUID();
        ItemStack proxy = source.prototype();
        proxy.setAmount(1);
        ItemMeta meta = proxy.getItemMeta();
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, leaseId.toString());
        proxy.setItemMeta(meta);
        inventory.setItem(inventorySlot, proxy);

        QuiverAmmoLease lease = new QuiverAmmoLease(
                leaseId, player.getUniqueId(), source, inventorySlot, weaponType, hand, currentTick);
        active.put(player.getUniqueId(), lease);
        return lease;
    }

    public QuiverAmmoLease active(Player player) {
        return player == null ? null : active.get(player.getUniqueId());
    }

    public QuiverAmmoLease active(UUID playerId) {
        return playerId == null ? null : active.get(playerId);
    }

    /**
     * Accepts only this lease's externally-marked candidate and strips the marker
     * before vanilla captures projectile data.
     */
    public boolean readyCandidate(Player player, ItemStack candidate) {
        QuiverAmmoLease lease = active(player);
        if (lease == null || lease.cleaned() || !hasMarker(candidate, lease.leaseId())) {
            return false;
        }

        ItemStack inventoryProxy = player.getInventory().getItem(lease.inventorySlot());
        if (!hasMarker(inventoryProxy, lease.leaseId())) {
            cleanup(player, lease.leaseId());
            return false;
        }
        // Validate both views before mutating either: Paper is free to expose the
        // same ItemStack object for the event candidate and the inventory slot.
        stripMarker(candidate);
        stripMarker(inventoryProxy);
        player.getInventory().setItem(lease.inventorySlot(), inventoryProxy);
        lease.markReady();
        return true;
    }

    /**
     * Restores the marker between vanilla's separate readiness checks. The marker
     * is always stripped synchronously when a candidate is accepted, so vanilla
     * never captures it as projectile data.
     */
    public boolean rearmMarker(Player player, UUID expectedLeaseId) {
        QuiverAmmoLease lease = active(player);
        if (lease == null || lease.cleaned() || !lease.ready()
                || expectedLeaseId == null || !expectedLeaseId.equals(lease.leaseId())) {
            return false;
        }
        ItemStack current = player.getInventory().getItem(lease.inventorySlot());
        if (!identity.sameIdentity(current, lease.source().prototype())) {
            return false;
        }
        ItemMeta meta = current.getItemMeta();
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, lease.leaseId().toString());
        current.setItemMeta(meta);
        player.getInventory().setItem(lease.inventorySlot(), current);
        return true;
    }

    public void markOperation(QuiverAmmoLease lease, Player player, boolean vanillaConsumptionExpected) {
        if (lease == null || player == null || active(player) != lease) {
            return;
        }
        ItemStack current = player.getInventory().getItem(lease.inventorySlot());
        int amount = identity.sameIdentity(current, lease.source().prototype()) ? current.getAmount() : 1;
        lease.markCommitted(vanillaConsumptionExpected, amount);
    }

    public boolean protectsSlot(Player player, int inventorySlot) {
        QuiverAmmoLease lease = active(player);
        return lease != null && lease.inventorySlot() == inventorySlot;
    }

    public boolean isSelectedIdentity(Player player, ItemStack item) {
        QuiverAmmoLease lease = active(player);
        return lease != null && identity.sameIdentity(lease.source().prototype(), item);
    }

    public boolean cleanup(Player player, UUID expectedLeaseId) {
        if (player == null) {
            return false;
        }
        QuiverAmmoLease lease = active.get(player.getUniqueId());
        if (lease == null || (expectedLeaseId != null && !expectedLeaseId.equals(lease.leaseId()))) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(lease.inventorySlot());
        boolean removed = false;
        if (hasMarker(current, lease.leaseId())) {
            inventory.setItem(lease.inventorySlot(), decrementOne(current));
            removed = true;
        } else if (lease.ready() && identity.sameIdentity(current, lease.source().prototype())) {
            boolean vanillaAlreadyConsumed = lease.vanillaConsumptionExpected()
                    && current.getAmount() < lease.amountBeforeVanillaConsumption();
            if (!vanillaAlreadyConsumed) {
                inventory.setItem(lease.inventorySlot(), decrementOne(current));
                removed = true;
            }
        }

        active.remove(player.getUniqueId());
        lease.markCleaned();
        return removed;
    }

    /** Removes the leased unit from PlayerDeathEvent's already-materialized drops. */
    public boolean removeProxyFromDrops(QuiverAmmoLease lease, List<ItemStack> drops) {
        if (lease == null || drops == null) {
            return false;
        }
        for (ListIterator<ItemStack> iterator = drops.listIterator(); iterator.hasNext();) {
            ItemStack drop = iterator.next();
            if (!hasMarker(drop, lease.leaseId())) {
                continue;
            }
            ItemStack remainder = decrementOne(drop);
            if (remainder == null) {
                iterator.remove();
            } else {
                stripMarker(remainder);
                iterator.set(remainder);
            }
            return true;
        }
        if (!lease.ready()) {
            return false;
        }
        for (ListIterator<ItemStack> iterator = drops.listIterator(); iterator.hasNext();) {
            ItemStack drop = iterator.next();
            if (!identity.sameIdentity(drop, lease.source().prototype())) {
                continue;
            }
            ItemStack remainder = decrementOne(drop);
            if (remainder == null) {
                iterator.remove();
            } else {
                iterator.set(remainder);
            }
            return true;
        }
        return false;
    }

    public int size() {
        return active.size();
    }

    public Set<UUID> activePlayerIds() {
        return Set.copyOf(active.keySet());
    }

    public boolean removeMarkerIfPresent(ItemStack item, UUID leaseId) {
        if (!hasMarker(item, leaseId)) {
            return false;
        }
        stripMarker(item);
        return true;
    }

    public boolean hasMarker(ItemStack item, UUID leaseId) {
        if (isEmpty(item) || leaseId == null || !item.hasItemMeta()) {
            return false;
        }
        String marker = item.getItemMeta().getPersistentDataContainer()
                .get(markerKey, PersistentDataType.STRING);
        return leaseId.toString().equals(marker);
    }

    private void stripMarker(ItemStack item) {
        if (isEmpty(item) || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(markerKey);
        item.setItemMeta(meta);
    }

    private static ItemStack decrementOne(ItemStack item) {
        if (isEmpty(item) || item.getAmount() <= 1) {
            return null;
        }
        ItemStack remainder = item.clone();
        remainder.setAmount(item.getAmount() - 1);
        return remainder;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
