package io.github.tootertutor.ModularPacks.model;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public class ModelManager implements Listener {
    private static final byte TAGGED_MODEL = (byte) 1;
    private static final long MAINTENANCE_PERIOD_TICKS = 20L;
    private static final float ROTATION_THRESHOLD = 1.5f;

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    private final Map<UUID, UUID> activeModels = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingRefreshes = new ConcurrentHashMap<>();
    private final BukkitTask maintenanceTask;

    public ModelManager(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
        cleanupTaggedArmorStands();
        this.maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOnlinePlayers,
                MAINTENANCE_PERIOD_TICKS, MAINTENANCE_PERIOD_TICKS);
    }

    public void scanPlayerForModels(Player player) {
        if (player == null)
            return;

        if (!plugin.cfg().renderModel()) {
            removeModel(player.getUniqueId());
            return;
        }

        ItemStack sourceBackpack = resolveVisibleBackpack(player);
        if (ItemStacks.isAir(sourceBackpack)) {
            removeModel(player.getUniqueId());
            return;
        }

        ArmorStand stand = ensureArmorStand(player, sourceBackpack);
        if (stand == null) {
            removeModel(player.getUniqueId());
            return;
        }

        applyVisualState(player, stand, sourceBackpack, shouldRender(player));
    }

    public void shutdown() {
        for (BukkitTask task : pendingRefreshes.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        pendingRefreshes.clear();

        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }

        for (UUID playerId : new ArrayList<>(activeModels.keySet())) {
            removeModel(playerId);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        queueRefresh(event.getPlayer(), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeModel(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        removeModel(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        queueRefresh(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        queueRefresh(event.getPlayer(), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        queueRefresh(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isWearableModel(event.getEntity()))
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!isWearableModel(event.getEntity()) && !isWearableModel(event.getTarget()))
            return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            queueRefresh(player, 1L);
        }
    }

    @EventHandler
    public void onToggleSwim(EntityToggleSwimEvent event) {
        if (event.getEntity() instanceof Player player) {
            queueRefresh(player, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!isWearableModel(event.getRightClicked()))
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!isWearableModel(event.getRightClicked()))
            return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null)
            return;

        Player player = event.getPlayer();
        boolean hasActiveModel = activeModels.containsKey(player.getUniqueId());
        boolean hasBackpack = !ItemStacks.isAir(resolveVisibleBackpack(player));
        if (!hasActiveModel && !hasBackpack)
            return;

        boolean rotationChanged = Math.abs(angleDelta(from.getYaw(), to.getYaw())) >= ROTATION_THRESHOLD
                || Math.abs(from.getPitch() - to.getPitch()) >= ROTATION_THRESHOLD;
        boolean forceUpdate = player.isGliding() || player.isSwimming();

        if (!rotationChanged && !forceUpdate) {
            return;
        }

        scanPlayerForModels(player);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        queueRefresh(event.getPlayer(), 1L);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        queueRefresh(event.getPlayer(), 1L);
    }

    @EventHandler
    public void onPoseChange(EntityPoseChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            queueRefresh(player, 1L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (ItemStacks.isAir(item))
            return;

        if (!backpackItems.isBackpack(item) && item.getType() != Material.TRIDENT)
            return;

        queueRefresh(event.getPlayer(), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean chestSlotTouched = event.getSlotType() == InventoryType.SlotType.ARMOR || event.getRawSlot() == 38;
        boolean backpackInvolved = backpackItems.isBackpack(event.getCurrentItem())
                || backpackItems.isBackpack(event.getCursor());

        if (event.isShiftClick() && (backpackInvolved || !ItemStacks.isAir(resolveVisibleBackpack(player)))) {
            queueRefresh(player, 1L);
            return;
        }

        if (chestSlotTouched || backpackInvolved) {
            queueRefresh(player, 1L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlots().contains(38)) {
            queueRefresh(player, 1L);
        }
    }

    private void refreshOnlinePlayers() {
        if (!plugin.cfg().renderModel()) {
            for (UUID playerId : new ArrayList<>(activeModels.keySet())) {
                removeModel(playerId);
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            scanPlayerForModels(player);
        }
    }

    private void queueRefresh(Player player, long delayTicks) {
        if (player == null)
            return;

        UUID playerId = player.getUniqueId();
        BukkitTask previous = pendingRefreshes.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRefreshes.remove(playerId);
            Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                scanPlayerForModels(onlinePlayer);
            } else {
                removeModel(playerId);
            }
        }, Math.max(1L, delayTicks));
        pendingRefreshes.put(playerId, task);
    }

    private boolean shouldRender(Player player) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR)
            return false;

        String criteria = plugin.cfg().renderCriteria();
        if (criteria == null)
            return true;

        float pitch = player.getLocation().getPitch();
        float configuredPitchMin = plugin.cfg().disablePitchMin();
        float configuredPitchMax = plugin.cfg().disablePitchMax();
        return switch (criteria) {
            case "EXACT_PITCH" -> Math.abs(pitch - configuredPitchMin) > 0.5F;
            case "PITCH" -> {
                float min = Math.min(configuredPitchMin, configuredPitchMax);
                float max = Math.max(configuredPitchMin, configuredPitchMax);

                // - Negative-side bound defines an upward hide limit (pitch < -upLimit)
                // - Positive-side bound defines a downward hide limit (pitch > downLimit)
                Float upLimit = min < 0.0F ? Math.abs(min) : null;
                Float downLimit = max > 0.0F ? max : null;

                boolean hideByUpPitch = upLimit != null && pitch < -upLimit;
                boolean hideByDownPitch = downLimit != null && pitch > downLimit;
                yield !(hideByUpPitch || hideByDownPitch);
            }
            default -> true;
        };
    }

    private ItemStack resolveVisibleBackpack(Player player) {
        if (player == null)
            return new ItemStack(org.bukkit.Material.AIR);

        ItemStack chestItem = player.getInventory().getChestplate();
        return backpackItems.isBackpack(chestItem) ? chestItem : new ItemStack(Material.AIR);
    }

    private ArmorStand ensureArmorStand(Player player, ItemStack sourceBackpack) {
        ArmorStand stand = getActiveStand(player.getUniqueId());
        if (stand == null || !stand.isValid() || stand.getWorld() != player.getWorld()) {
            removeModel(player.getUniqueId());
            stand = spawnArmorStand(player, sourceBackpack);
        }

        if (stand == null)
            return null;

        if (!player.getPassengers().contains(stand)) {
            player.addPassenger(stand);
        }

        applyEntityTags(stand, player, sourceBackpack);
        return stand;
    }

    private ArmorStand spawnArmorStand(Player player, ItemStack sourceBackpack) {
        Location spawnLocation = player.getLocation();
        Entity entity = player.getWorld().spawn(spawnLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.customName(null);
        });

        if (!(entity instanceof ArmorStand stand))
            return null;

        applyEntityTags(stand, player, sourceBackpack);
        activeModels.put(player.getUniqueId(), stand.getUniqueId());
        player.addPassenger(stand);
        return stand;
    }

    private void applyVisualState(Player player, ArmorStand stand, ItemStack sourceBackpack, boolean visible) {
        Location playerLocation = player.getLocation();

        if (stand.getVehicle() != player) {
            stand.teleport(playerLocation);
        }

        float targetYaw = playerLocation.getYaw();
        float targetPitch = 0.0F;
        Location standLocation = stand.getLocation();
        if (Math.abs(angleDelta(standLocation.getYaw(), targetYaw)) >= ROTATION_THRESHOLD
                || Math.abs(standLocation.getPitch() - targetPitch) >= ROTATION_THRESHOLD) {
            stand.setRotation(targetYaw, targetPitch);
        }

        stand.setHeadPose(resolveHeadPose(player));
        if (!player.getPassengers().contains(stand)) {
            player.addPassenger(stand);
        }

        syncHelmetVisibility(stand, sourceBackpack, visible);
    }

    private void syncHelmetVisibility(ArmorStand stand, ItemStack sourceBackpack, boolean visible) {
        if (stand.getEquipment() == null) {
            return;
        }

        ItemStack current = stand.getEquipment().getHelmet();
        if (!visible) {
            if (!ItemStacks.isAir(current)) {
                stand.getEquipment().setHelmet(null);
            }
            return;
        }

        ItemStack wearableItem = backpackItems.createWearableModel(sourceBackpack);
        if (ItemStacks.isAir(wearableItem)) {
            stand.getEquipment().setHelmet(null);
            return;
        }

        if (ItemStacks.isAir(current) || !wearableItem.isSimilar(current)) {
            stand.getEquipment().setHelmet(wearableItem);
        }
    }

    private EulerAngle resolveHeadPose(Player player) {
        if (player.isGliding()) {
            return new EulerAngle(Math.toRadians(15.0D), 0.0D, 0.0D);
        }
        if (player.isSwimming()) {
            return new EulerAngle(Math.toRadians(30.0D), 0.0D, 0.0D);
        }
        if (player.isSneaking()) {
            return new EulerAngle(Math.toRadians(8.0D), 0.0D, 0.0D);
        }
        return EulerAngle.ZERO;
    }

    private void removeModel(UUID playerId) {
        if (playerId == null)
            return;

        BukkitTask pending = pendingRefreshes.remove(playerId);
        if (pending != null) {
            pending.cancel();
        }

        UUID entityId = activeModels.remove(playerId);
        if (entityId == null)
            return;

        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null)
            return;

        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            vehicle.removePassenger(entity);
        }
        entity.remove();
    }

    private ArmorStand getActiveStand(UUID playerId) {
        UUID entityId = activeModels.get(playerId);
        if (entityId == null)
            return null;
        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof ArmorStand stand) {
            return stand;
        }
        return null;
    }

    private boolean isWearableModel(Entity entity) {
        if (!(entity instanceof ArmorStand stand))
            return false;
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        Byte tagged = pdc.get(plugin.keys().WEARABLE_MODEL, PersistentDataType.BYTE);
        return tagged != null && tagged == TAGGED_MODEL;
    }

    private void applyEntityTags(ArmorStand stand, Player owner, ItemStack sourceBackpack) {
        Keys keys = plugin.keys();
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        pdc.set(keys.WEARABLE_MODEL, PersistentDataType.BYTE, TAGGED_MODEL);
        pdc.set(keys.WEARABLE_MODEL_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        String backpackId = readBackpackId(sourceBackpack);
        if (backpackId != null) {
            pdc.set(keys.WEARABLE_MODEL_BACKPACK_ID, PersistentDataType.STRING, backpackId);
        } else {
            pdc.remove(keys.WEARABLE_MODEL_BACKPACK_ID);
        }
    }

    private String readBackpackId(ItemStack item) {
        if (!backpackItems.isBackpack(item))
            return null;
        ItemStack source = item;
        ItemStack metaSource = source;
        return metaSource.getItemMeta().getPersistentDataContainer().get(plugin.keys().BACKPACK_ID,
                PersistentDataType.STRING);
    }

    private void cleanupTaggedArmorStands() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (isWearableModel(stand)) {
                    stand.remove();
                }
            }
        }

    }

    private static float angleDelta(float from, float to) {
        float delta = (to - from) % 360.0F;
        if (delta >= 180.0F)
            delta -= 360.0F;
        if (delta < -180.0F)
            delta += 360.0F;
        return delta;
    }
}