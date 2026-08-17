package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.event.player.PlayerReadyArrowEvent;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;

/** Event-driven Quiver integration that delegates projectile behavior to vanilla. */
public final class QuiverService implements Listener {

    private static final int LEASE_TIMEOUT_TICKS = 600;
    private static final int MESSAGE_THROTTLE_TICKS = 60;

    private final ModularPacksPlugin plugin;
    private final QuiverAmmoService ammoService;
    private final QuiverSelectionCodec selectionCodec;
    private final QuiverSourceResolver sourceResolver;
    private final QuiverAmmoLeaseManager leases;
    private final QuiverSelectionMenu selectionMenu;
    private final Map<UUID, Integer> lastFailureMessageTick = new HashMap<>();
    private BukkitTask watchdog;

    public QuiverService(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.ammoService = new QuiverAmmoService(plugin.backpackStorage());
        this.selectionCodec = new QuiverSelectionCodec();
        this.sourceResolver = new QuiverSourceResolver(plugin, ammoService, selectionCodec);
        this.leases = new QuiverAmmoLeaseManager(plugin.keys().QUIVER_PROXY_ID,
                plugin.backpackStorage().identity());
        this.selectionMenu = new QuiverSelectionMenu(plugin, ammoService, sourceResolver);
    }

    public void start() {
        if (watchdog != null) {
            return;
        }
        watchdog = Bukkit.getScheduler().runTaskTimer(plugin, this::expireLeases, 20L, 20L);
    }

    public void shutdown() {
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        for (UUID playerId : leases.activePlayerIds()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                cleanup(player, null, "plugin disable");
            }
        }
        lastFailureMessageTick.clear();
    }

    public QuiverSelectionMenu selectionMenu() {
        return selectionMenu;
    }

    public void openSelection(Player player, UUID backpackId, String backpackType, UUID moduleId) {
        cleanup(player, null, "selection menu opened");
        selectionMenu.open(player, backpackId, backpackType, moduleId);
    }

    public boolean isProxySlot(Player player, int inventorySlot) {
        return leases.protectsSlot(player, inventorySlot);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWeaponInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        ItemStack weapon = event.getItem();
        if (hand == null || ItemStacks.isAir(weapon)
                || weapon.getType() != Material.BOW && weapon.getType() != Material.CROSSBOW) {
            return;
        }
        if (event.useItemInHand() == Result.DENY || isLoadedCrossbow(weapon)) {
            return;
        }

        Player player = event.getPlayer();
        QuiverAmmoLease active = leases.active(player);
        if (player.getGameMode() == GameMode.CREATIVE) {
            if (active != null) {
                cleanup(player, active.leaseId(), "creative-mode vanilla bypass");
            }
            return;
        }
        if (active != null) {
            if (active.weaponType() == weapon.getType() && active.hand() == hand) {
                if (sourceStillAvailable(player, active)) {
                    return;
                }
                cleanup(player, active.leaseId(), "Quiver source no longer available");
            } else {
                cleanup(player, active.leaseId(), "new weapon use");
            }
        }

        QuiverSource source;
        try {
            source = sourceResolver.resolve(player);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Unable to resolve Quiver ammunition: " + ex.getMessage());
            return;
        }
        if (source == null) {
            return;
        }
        int emptySlot = player.getInventory().firstEmpty();
        if (emptySlot < 0 || emptySlot >= 36) {
            showNoSlotMessage(player);
            return;
        }

        QuiverAmmoLease lease = leases.stage(
                player, source, emptySlot, weapon.getType(), hand, Bukkit.getCurrentTick());
        if (lease != null) {
            debug("lease prepared player=" + player.getUniqueId() + " slot=" + emptySlot
                    + " backpack=" + source.backpackId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWeaponInteractFinal(PlayerInteractEvent event) {
        if (event.useItemInHand() == Result.DENY) {
            QuiverAmmoLease lease = leases.active(event.getPlayer());
            if (lease != null) {
                cleanup(event.getPlayer(), lease.leaseId(), "interaction denied");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onReadyArrow(PlayerReadyArrowEvent event) {
        Player player = event.getPlayer();
        QuiverAmmoLease lease = leases.active(player);
        if (lease != null && player.getGameMode() == GameMode.CREATIVE) {
            // A stale lease must never block creative's synthetic ammunition. If
            // Paper is currently offering the proxy, sanitize it before cleanup.
            if (leases.hasMarker(event.getArrow(), lease.leaseId())) {
                leases.readyCandidate(player, event.getArrow());
                leases.removeMarkerIfPresent(event.getArrow(), lease.leaseId());
            }
            cleanup(player, lease.leaseId(), "creative-mode arrow-ready bypass");
            return;
        }
        if (lease == null || event.getBow().getType() != lease.weaponType() || event.isCancelled()) {
            return;
        }
        if (!sourceStillAvailable(player, lease)) {
            boolean proxyCandidate = leases.hasMarker(event.getArrow(), lease.leaseId());
            cleanup(player, lease.leaseId(), "Quiver source unavailable at arrow-ready");
            if (proxyCandidate) {
                event.setCancelled(true);
            }
            return;
        }
        if (leases.readyCandidate(player, event.getArrow())) {
            debug("proxy candidate selected player=" + player.getUniqueId());
            UUID leaseId = lease.leaseId();
            Bukkit.getScheduler().runTask(plugin, () -> leases.rearmMarker(player, leaseId));
            return;
        }

        // Force vanilla to continue its normal candidate scan until it reaches the
        // externally tracked proxy. No arbitrary ItemStack is injected into the event.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBowShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        QuiverAmmoLease lease = leases.active(player);
        if (lease == null || lease.weaponType() != Material.BOW) {
            return;
        }
        if (event.getHand() != lease.hand() || event.getBow().getType() != lease.weaponType()) {
            event.setCancelled(true);
            scheduleCleanup(player, lease, "unexpected bow or hand");
            return;
        }
        if (event.isCancelled()) {
            scheduleCleanup(player, lease, "bow shot cancelled");
            return;
        }
        ItemStack consumable = event.getConsumable();
        if (!lease.ready() || consumable == null
                || !plugin.backpackStorage().identity().sameIdentity(consumable, lease.source().prototype())) {
            event.setCancelled(true);
            scheduleCleanup(player, lease, "unexpected bow consumable");
            return;
        }

        boolean consume = event.shouldConsumeItem();
        boolean sourceValid = updateLogicalAmmo(player, lease, consume);
        if (!sourceValid) {
            // At MONITOR this is intentionally conservative: an arrow removed during
            // the draw must not become a free shot.
            event.setCancelled(true);
            scheduleCleanup(player, lease, "logical ammo unavailable");
            debug("logical ammo unavailable; bow shot cancelled player=" + player.getUniqueId());
            return;
        }

        leases.markOperation(lease, player, consume);
        scheduleCleanup(player, lease, consume ? "bow shot committed" : "bow shot non-consuming");
        debug("bow shot committed player=" + player.getUniqueId() + " consume=" + consume);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCrossbowLoad(EntityLoadCrossbowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        QuiverAmmoLease lease = leases.active(player);
        if (lease == null || lease.weaponType() != Material.CROSSBOW) {
            return;
        }
        if (event.getHand() != lease.hand() || event.getCrossbow().getType() != lease.weaponType()) {
            event.setCancelled(true);
            scheduleCleanup(player, lease, "unexpected crossbow or hand");
            return;
        }
        if (event.isCancelled()) {
            scheduleCleanup(player, lease, "crossbow load cancelled");
            return;
        }
        if (!lease.ready()) {
            event.setCancelled(true);
            scheduleCleanup(player, lease, "crossbow proxy not selected");
            return;
        }

        boolean consume = event.shouldConsumeItem();
        boolean sourceValid = updateLogicalAmmo(player, lease, consume);
        if (!sourceValid) {
            event.setCancelled(true);
            scheduleCleanup(player, lease, "logical ammo unavailable");
            debug("logical ammo unavailable; crossbow load cancelled player=" + player.getUniqueId());
            return;
        }

        leases.markOperation(lease, player, consume);
        scheduleCrossbowSanitization(event.getCrossbow(), lease);
        scheduleCleanup(player, lease, consume ? "crossbow load committed" : "crossbow load non-consuming");
        debug("crossbow load committed player=" + player.getUniqueId() + " consume=" + consume);
    }

    @EventHandler
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        QuiverAmmoLease lease = leases.active(event.getPlayer());
        if (lease != null) {
            scheduleCleanup(event.getPlayer(), lease, "weapon use stopped");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        QuiverAmmoLease lease = leases.active(player);
        if (lease == null) {
            return;
        }
        boolean leasedClick = event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && leases.protectsSlot(player, event.getSlot());
        boolean leasedHotbar = leases.protectsSlot(player, event.getHotbarButton());
        boolean collectSelected = event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && leases.isSelectedIdentity(player, event.getCursor());
        if (leasedClick || leasedHotbar || collectSelected) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || leases.active(player) == null) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize && leases.protectsSlot(player, event.getView().convertSlot(rawSlot))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && leases.active(player) != null
                && leases.isSelectedIdentity(player, event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        cleanup(event.getPlayer(), null, "held slot changed");
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        cleanup(event.getPlayer(), null, "game mode changed");
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        cleanup(event.getPlayer(), null, "hands swapped");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        QuiverAmmoLease lease = leases.active(event.getPlayer());
        if (lease == null) {
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (leases.hasMarker(dropped, lease.leaseId())
                || event.getPlayer().getInventory().getHeldItemSlot() == lease.inventorySlot()) {
            event.setCancelled(true);
            cleanup(event.getPlayer(), lease.leaseId(), "proxy drop blocked");
        } else if (dropped.getType() == lease.weaponType()) {
            cleanup(event.getPlayer(), lease.leaseId(), "weapon dropped");
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cleanup(player, null, "inventory opened");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        cleanup(event.getPlayer(), null, "teleport");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        QuiverAmmoLease lease = leases.active(event.getEntity());
        if (lease != null) {
            leases.removeProxyFromDrops(lease, event.getDrops());
        }
        cleanup(event.getEntity(), null, "death");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer(), null, "disconnect");
        lastFailureMessageTick.remove(event.getPlayer().getUniqueId());
    }

    private void expireLeases() {
        int now = Bukkit.getCurrentTick();
        for (UUID playerId : leases.activePlayerIds()) {
            QuiverAmmoLease lease = leases.active(playerId);
            if (lease == null || now - lease.createdTick() < LEASE_TIMEOUT_TICKS) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                cleanup(player, lease.leaseId(), "lease timeout");
            }
        }
    }

    private void scheduleCleanup(Player player, QuiverAmmoLease lease, String reason) {
        UUID leaseId = lease.leaseId();
        Bukkit.getScheduler().runTask(plugin, () -> cleanup(player, leaseId, reason));
    }

    private boolean updateLogicalAmmo(Player player, QuiverAmmoLease lease, boolean consume) {
        try {
            return consume
                    ? sourceResolver.consumeOne(player, lease.source())
                    : sourceResolver.isAvailable(player, lease.source());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Unable to commit Quiver ammunition: " + ex.getMessage());
            return false;
        }
    }

    private boolean sourceStillAvailable(Player player, QuiverAmmoLease lease) {
        try {
            return sourceResolver.isAvailable(player, lease.source());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Unable to revalidate Quiver ammunition: " + ex.getMessage());
            return false;
        }
    }

    private void cleanup(Player player, UUID expectedLeaseId, String reason) {
        QuiverAmmoLease before = leases.active(player);
        if (before == null || (expectedLeaseId != null && !expectedLeaseId.equals(before.leaseId()))) {
            return;
        }
        boolean removed = leases.cleanup(player, expectedLeaseId);
        debug("lease aborted/cleaned player=" + player.getUniqueId() + " reason=" + reason
                + " proxyRemoved=" + removed);
    }

    private void scheduleCrossbowSanitization(ItemStack crossbow, QuiverAmmoLease lease) {
        UUID leaseId = lease.leaseId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(crossbow.getItemMeta() instanceof CrossbowMeta meta) || !meta.hasChargedProjectiles()) {
                return;
            }
            List<ItemStack> sanitized = new ArrayList<>(meta.getChargedProjectiles().size());
            boolean changed = false;
            for (ItemStack projectile : meta.getChargedProjectiles()) {
                ItemStack clean = projectile.clone();
                changed |= leases.removeMarkerIfPresent(clean, leaseId);
                sanitized.add(clean);
            }
            if (changed) {
                meta.setChargedProjectiles(sanitized);
                crossbow.setItemMeta(meta);
                debug("stripped unexpected proxy marker from charged crossbow");
            }
        });
    }

    private void showNoSlotMessage(Player player) {
        int now = Bukkit.getCurrentTick();
        int last = lastFailureMessageTick.getOrDefault(player.getUniqueId(), Integer.MIN_VALUE / 2);
        if (now - last < MESSAGE_THROTTLE_TICKS) {
            return;
        }
        lastFailureMessageTick.put(player.getUniqueId(), now);
        player.sendActionBar(Text.c("&cQuiver needs one empty inventory slot to stage ammunition."));
    }

    private static boolean isLoadedCrossbow(ItemStack weapon) {
        return weapon.getType() == Material.CROSSBOW
                && weapon.getItemMeta() instanceof CrossbowMeta meta
                && meta.hasChargedProjectiles();
    }

    private void debug(String message) {
        if (plugin.cfg().debugClickLog()) {
            plugin.getLogger().info("[Quiver] " + message);
        }
    }
}
