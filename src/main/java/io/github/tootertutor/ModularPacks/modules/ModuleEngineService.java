package io.github.tootertutor.ModularPacks.modules;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;
import io.github.tootertutor.ModularPacks.gui.ScreenRouter;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.modules.crafting.AutocraftingStateCodec;
import io.github.tootertutor.ModularPacks.modules.crafting.CraftingModuleLogic;
import io.github.tootertutor.ModularPacks.modules.feeding.FeedingEngine;
import io.github.tootertutor.ModularPacks.modules.furnace.FurnaceEngine;
import io.github.tootertutor.ModularPacks.modules.furnace.FurnaceModule;
import io.github.tootertutor.ModularPacks.modules.jukebox.JukeboxEngine;
import io.github.tootertutor.ModularPacks.modules.magnet.MagnetVoidEngine;
import io.github.tootertutor.ModularPacks.modules.restock.RestockEngine;
import io.github.tootertutor.ModularPacks.modules.tank.TankExperience;
import io.github.tootertutor.ModularPacks.modules.tank.TankModuleLogic;
import io.github.tootertutor.ModularPacks.modules.tank.TankStateCodec;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

/**
 * Periodically ticks open module screens and passive backpack modules.
 * Mutation-sensitive logic is skipped while the affected backpack GUI is open.
 */
public final class ModuleEngineService {

    private static final long ENGINE_PERIOD_TICKS = 10L;
    private static final int ENGINE_DT_TICKS = 10;
    private static final int MAX_EXP_PUMP_TARGET_LEVEL = 100;

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;
    private final FeedingEngine feedingEngine;
    private final JukeboxEngine jukeboxEngine;
    private final MagnetVoidEngine magnetVoidEngine;
    private final FurnaceEngine furnaceEngine;
    private final RestockEngine restockEngine;
    private final ScreenRouter screenRouter;
    private final ConcurrentMap<UUID, Integer> autocraftingCooldownTicks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> pumpCooldownTicks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> expPumpCooldownTicks = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ModuleEngineService(ModularPacksPlugin plugin, ScreenRouter screenRouter) {
        this.plugin = plugin;
        this.screenRouter = screenRouter;
        this.backpackItems = new BackpackItems(plugin);
        this.feedingEngine = new FeedingEngine(plugin);
        this.jukeboxEngine = new JukeboxEngine(plugin);
        this.magnetVoidEngine = new MagnetVoidEngine(plugin);
        this.furnaceEngine = new FurnaceEngine(plugin);
        this.restockEngine = new RestockEngine(plugin);
    }

    public void start() {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickOpenScreens, ENGINE_PERIOD_TICKS,
                ENGINE_PERIOD_TICKS);
    }

    public void stop() {
        if (task != null)
            task.cancel();
        task = null;
    }

    private void tickOpenScreens() {
        Set<UUID> openModuleIds = new HashSet<>();
        Set<UUID> openBackpackIds = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof BackpackMenuHolder bmh) {
                openBackpackIds.add(bmh.backpackId());
            }
            // Check FurnaceModule instance from ScreenRouter
            FurnaceModule furnaceModule = screenRouter.getFurnaceModule();
            if (furnaceModule != null && furnaceModule.hasSession(player)) {
                UUID backpackId = furnaceModule.getSessionBackpackId(player);
                UUID moduleId = furnaceModule.getSessionModuleId(player);
                String backpackType = furnaceModule.getSessionBackpackType(player);
                ScreenType st = furnaceModule.getSessionScreenType(player);
                if (moduleId != null && backpackId != null && backpackType != null && st != null) {
                    openModuleIds.add(moduleId);
                    if (st == ScreenType.SMELTING || st == ScreenType.BLASTING || st == ScreenType.SMOKING) {
                        furnaceEngine.tickFurnaceScreen(player, backpackId, backpackType,
                                moduleId, st, top, ENGINE_DT_TICKS);
                    }
                }
                continue;
            }

            if (!(top.getHolder() instanceof ModuleScreenHolder msh))
                continue;

            openModuleIds.add(msh.moduleId());

            ScreenType st = msh.screenType();
            if (st == ScreenType.SMELTING || st == ScreenType.BLASTING || st == ScreenType.SMOKING) {
                furnaceEngine.tickFurnaceScreen(player, msh.backpackId(), msh.backpackType(), msh.moduleId(), st, top,
                        ENGINE_DT_TICKS);
            }

            // Later: add other engines here (stonecutter, smithing, etc.)
        }

        tickCarriedBackpacks(openModuleIds, openBackpackIds);
        tickPlacedBackpacks(openModuleIds, openBackpackIds);
    }

    private void tickCarriedBackpacks(Set<UUID> openModuleIds, Set<UUID> openBackpackIds) {
        Keys keys = plugin.keys();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<UUID> processedBackpacks = new HashSet<>();
            ItemStack[] contents = player.getInventory().getContents();
            if (contents == null || contents.length == 0)
                continue;

            // If the backpack that was providing music is no longer in the player's
            // inventory, stop the track.
            jukeboxEngine.stopIfActiveBackpackMissing(player, contents);

            for (ItemStack item : contents) {
                UUID backpackId = readBackpackId(keys, item);
                if (backpackId == null)
                    continue;
                if (!processedBackpacks.add(backpackId))
                    continue;

                String backpackType = readBackpackType(keys, item);
                if (backpackType == null || backpackType.isBlank())
                    continue;

                tickBackpack(player, backpackId, backpackType, openModuleIds, openBackpackIds);
            }
        }

        // Cleanup stale entries for offline players
        jukeboxEngine.cleanupOfflinePlayers();
    }

    private void tickPlacedBackpacks(Set<UUID> openModuleIds, Set<UUID> openBackpackIds) {
        // Keep block/render tracking in sync with world physics (water flow, support
        // loss, etc.) and clean up/drop items when placements break outside player
        // interactions.
        plugin.placedBackpacks().tickPlacedBackpacks(openModuleIds, openBackpackIds);

        // Tick placed backpacks with special module logic for block-form backpacks
        var placedBackpacks = plugin.placedBackpacks().getAllPlaced();

        for (var placed : placedBackpacks.values()) {
            if (!placed.isValid()) {
                continue;
            }

            // Placed backpacks tick with null player - modules need to handle this case
            tickBackpack(null, placed.backpackId(), placed.backpackType(), openModuleIds, openBackpackIds);
        }
    }

    private void tickBackpack(
            Player player,
            UUID backpackId,
            String backpackType,
            Set<UUID> openModuleIds,
            Set<UUID> openBackpackIds) {

        var typeDef = plugin.cfg().findType(backpackType);
        if (typeDef == null)
            return;

        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);

        boolean allowContentsMutations = openBackpackIds == null || !openBackpackIds.contains(backpackId);

        boolean changedAny = false;

        // Passive modules that mutate backpack contents (skip while that backpack GUI
        // is open)
        if (allowContentsMutations) {
            ItemStack[] logical = ensureLogicalContentsSize(data, typeDef.rows() * 9);

            UUID voidId = findInstalledModuleId(data, "Void");
            Set<Material> voidWhitelist = (voidId == null) ? Set.of() : readWhitelistFromState(data, voidId);

            // Feeding module only works when player is present (carried backpacks)
            if (player != null) {
                UUID feedingId = findInstalledModuleId(data, "Feeding");
                if (feedingId != null) {
                    ItemStack feedingSnapshot = resolveModuleSnapshotItem(data, feedingId);
                    List<Material> orderedWhitelist = readWhitelistOrderedFromState(data, feedingId);
                    changedAny |= feedingEngine.applyFeeding(player, logical, feedingSnapshot, orderedWhitelist);
                }
            }

            // Magnet module works differently for placed vs carried backpacks
            UUID magnetId = findInstalledModuleId(data, "Magnet");
            if (magnetId != null) {
                ItemStack magnetSnapshot = resolveModuleSnapshotItem(data, magnetId);
                ItemStack voidSnapshot = (voidId == null) ? null : resolveModuleSnapshotItem(data, voidId);

                if (player != null) {
                    // Carried backpack: use player location
                    changedAny |= magnetVoidEngine.applyMagnet(player, logical, readWhitelistFromState(data, magnetId),
                            magnetSnapshot, backpackId, backpackType, voidId, voidWhitelist, voidSnapshot);
                } else {
                    // Placed backpack: use block location
                    changedAny |= applyPlacedBackpackMagnet(backpackId, logical, magnetSnapshot, voidId, voidWhitelist,
                            voidSnapshot, data);
                }
            }

            // Restock module only works when player is present (carried backpacks)
            if (player != null) {
                UUID restockId = findInstalledModuleId(data, "Restock");
                if (restockId != null) {
                    int threshold = readRestockThresholdFromState(data, restockId);
                    java.util.List<ItemStack> whitelist = readRestockWhitelistFromState(data, restockId);
                    changedAny |= restockEngine.applyRestock(player, logical, threshold, whitelist);
                }

                UUID pumpId = findInstalledModuleId(data, "Pump");
                if (pumpId != null) {
                    changedAny |= applyFluidPump(player, data, pumpId);
                }

                UUID expPumpId = findInstalledModuleId(data, "ExpPump");
                if (expPumpId != null) {
                    changedAny |= applyExpPump(player, data, expPumpId);
                }
            }

            UUID autocraftingId = findInstalledModuleId(data, "Autocrafting");
            if (autocraftingId != null && (openModuleIds == null || !openModuleIds.contains(autocraftingId))) {
                changedAny |= applyAutocrafting(player, data, autocraftingId, logical);
            }

            if (changedAny) {
                data.contentsBytes(ItemStackCodec.toBytes(logical));
            }
        }

        // Ticking module states (furnace-like) is safe even if backpack GUI is open.
        changedAny |= furnaceEngine.tickInstalledFurnaces(data, openModuleIds, ENGINE_DT_TICKS);

        // Jukebox only works when player is present (carried backpacks)
        if (player != null) {
            UUID jukeboxId = findInstalledModuleId(data, "Jukebox");
            ItemStack jukeboxSnapshot = jukeboxId == null ? null : resolveModuleSnapshotItem(data, jukeboxId);
            jukeboxEngine.tickJukebox(player, backpackId, data, jukeboxId, jukeboxSnapshot);
        }

        if (changedAny) {
            plugin.repo().saveBackpack(data);
            if (player != null) {
                refreshBackpackItemsFor(player, backpackId, typeDef, data);
            }
            plugin.sessions().refreshLinkedBackpacksThrottled(backpackId, data);
        }
    }

    private boolean applyPlacedBackpackMagnet(UUID backpackId, ItemStack[] logical, ItemStack magnetSnapshot,
            UUID voidId, Set<Material> voidWhitelist, ItemStack voidSnapshot, BackpackData data) {
        // Get the placed backpack location
        var placedBackpack = plugin.placedBackpacks().getAllPlaced().values().stream()
                .filter(pb -> pb.backpackId().equals(backpackId))
                .findFirst()
                .orElse(null);

        if (placedBackpack == null || !placedBackpack.isValid()) {
            return false;
        }

        // Use a special magnet implementation for placed backpacks
        return magnetVoidEngine.applyMagnetAtLocation(placedBackpack.location(), logical,
                readWhitelistFromState(data, findInstalledModuleId(data, "Magnet")),
                magnetSnapshot, backpackId, data.backpackType(), voidId, voidWhitelist, voidSnapshot);
    }

    private boolean applyAutocrafting(Player player, BackpackData data, UUID moduleId, ItemStack[] logical) {
        if (data == null || moduleId == null || logical == null)
            return false;

        byte[] rawState = data.moduleStates().get(moduleId);
        AutocraftingStateCodec.State state = AutocraftingStateCodec.decode(rawState);

        int intervalTicks = Math.max(10, plugin.getConfig().getInt("Upgrades.Autocrafting.CraftingIntervalTicks", 120));
        int persistedCooldown = Math.max(0, state.cooldownTicks());
        int cooldown = autocraftingCooldownTicks.compute(moduleId,
                (id, existing) -> existing != null ? existing : persistedCooldown);
        cooldown = Math.max(0, cooldown - ENGINE_DT_TICKS);

        boolean changed = state.inventoryItems() != null && state.inventoryItems().length != 10;
        ItemStack[] moduleInv = ensureCraftingInventorySize(state.inventoryItems());

        if (cooldown <= 0) {
            int desired = AutocraftingStateCodec.clampDesiredAmount(state.desiredAmount());
            int craftedItems = craftAutocraftingBatch(player, moduleInv, logical, desired);
            if (craftedItems > 0) {
                changed = true;
                cooldown = intervalTicks;
            }
        }

        if (cooldown > 0) {
            autocraftingCooldownTicks.put(moduleId, cooldown);
        } else {
            autocraftingCooldownTicks.remove(moduleId);
        }

        if (changed) {
            data.moduleStates().put(moduleId,
                    AutocraftingStateCodec.encode(
                            new AutocraftingStateCodec.State(moduleInv,
                                    AutocraftingStateCodec.clampDesiredAmount(state.desiredAmount()),
                                    Math.max(0, cooldown))));
        }

        return changed;
    }

    private boolean applyFluidPump(Player player, BackpackData data, UUID pumpModuleId) {
        if (player == null || data == null || pumpModuleId == null)
            return false;

        int interval = Math.max(10, plugin.getConfig().getInt("Upgrades.Pump.IntervalTicks", 40));
        int cooldown = pumpCooldownTicks.getOrDefault(pumpModuleId, 0);
        cooldown = Math.max(0, cooldown - ENGINE_DT_TICKS);
        if (cooldown > 0) {
            pumpCooldownTicks.put(pumpModuleId, cooldown);
            return false;
        }

        UUID tankModuleId = findFluidTankModuleId(data);
        if (tankModuleId == null)
            return false;

        TankStateCodec.State state = TankStateCodec.decode(data.moduleStates().get(tankModuleId));
        if (state == null)
            return false;

        // Legacy mixed tank cannot be pumped as fluid while currently in EXP mode.
        if (state.expMode || state.expTotalPoints > 0)
            return false;

        String mode = resolvePumpMode(data, pumpModuleId, "Pump");
        boolean changed = false;
        if ("WITHDRAW".equals(mode)) {
            changed = withdrawFluidToInventory(player, state);
        } else {
            changed = depositFluidFromInventory(player, state);
        }

        if (!changed)
            return false;

        state.fluidBuckets = Math.max(0, Math.min(TankModuleLogic.MAX_FLUID_BUCKETS, state.fluidBuckets));
        if (state.fluidBuckets <= 0) {
            state.fluidBuckets = 0;
            state.fluidBucketMaterial = null;
        }
        state.expMode = false;
        state.expTotalPoints = 0;

        data.moduleStates().put(tankModuleId, TankStateCodec.encode(state));
        updateTankSnapshot(data, tankModuleId, state);

        pumpCooldownTicks.put(pumpModuleId, interval);
        return true;
    }

    private boolean applyExpPump(Player player, BackpackData data, UUID expPumpModuleId) {
        if (player == null || data == null || expPumpModuleId == null)
            return false;

        int interval = Math.max(10, plugin.getConfig().getInt("Upgrades.ExpPump.IntervalTicks", 40));
        int cooldown = expPumpCooldownTicks.getOrDefault(expPumpModuleId, 0);
        cooldown = Math.max(0, cooldown - ENGINE_DT_TICKS);
        if (cooldown > 0) {
            expPumpCooldownTicks.put(expPumpModuleId, cooldown);
            return false;
        }

        UUID tankModuleId = findExperienceTankModuleId(data);
        if (tankModuleId == null)
            return false;

        TankStateCodec.State state = TankStateCodec.decode(data.moduleStates().get(tankModuleId));
        if (state == null)
            return false;

        String mode = resolvePumpMode(data, expPumpModuleId, "ExpPump", true);
        boolean changed = false;
        if ("WITHDRAW".equals(mode)) {
            int pointsForOneLevel = pointsForOneDisplayedLevelGain(player);
            if (pointsForOneLevel > 0 && state.expTotalPoints > 0) {
                int granted = Math.min(pointsForOneLevel, state.expTotalPoints);
                if (granted > 0) {
                    state.expTotalPoints -= granted;
                    int remaining = granted;
                    if (isExpPumpMendingEnabled(data, expPumpModuleId)) {
                        int mendingBudget = Math.min(granted, resolveExpPumpMendingBudgetPerCycle());
                        int mendingLeftover = applyMendingToEquippedItems(player, mendingBudget);
                        remaining = (granted - mendingBudget) + mendingLeftover;
                    }
                    if (remaining > 0) {
                        player.giveExp(remaining);
                    }
                    changed = true;
                }
            }
        } else if ("KEEP_LEVEL".equals(mode)) {
            changed = maintainPlayerLevel(player, data, expPumpModuleId, state);
        } else {
            int pointsForOneLevel = pointsForOneDisplayedLevelLoss(player);
            if (pointsForOneLevel > 0 && state.expTotalPoints < TankModuleLogic.MAX_EXP_POINTS) {
                int room = TankModuleLogic.MAX_EXP_POINTS - state.expTotalPoints;
                int moved = Math.min(room, pointsForOneLevel);
                if (moved > 0) {
                    state.expTotalPoints += moved;
                    player.giveExp(-moved);
                    changed = true;
                }
            }
        }

        if (!changed)
            return false;

        state.expTotalPoints = Math.max(0, Math.min(TankModuleLogic.MAX_EXP_POINTS, state.expTotalPoints));
        state.expMode = true;
        state.fluidBuckets = 0;
        state.fluidBucketMaterial = null;

        data.moduleStates().put(tankModuleId, TankStateCodec.encode(state));
        updateTankSnapshot(data, tankModuleId, state);

        expPumpCooldownTicks.put(expPumpModuleId, interval);
        return true;
    }

    private boolean isExpPumpMendingEnabled(BackpackData data, UUID expPumpModuleId) {
        if (data == null || expPumpModuleId == null)
            return plugin.getConfig().getBoolean("Upgrades.ExpPump.MendEquippedItems", false);

        boolean fallback = plugin.getConfig().getBoolean("Upgrades.ExpPump.MendEquippedItems", false);
        ItemStack moduleItem = resolveModuleSnapshotItem(data, expPumpModuleId);
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return fallback;

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return fallback;

        Byte b = meta.getPersistentDataContainer().get(plugin.keys().MODULE_EXP_PUMP_MENDING,
                PersistentDataType.BYTE);
        if (b == null)
            return fallback;
        return b == 1;
    }

    private int applyMendingToEquippedItems(Player player, int xpPoints) {
        if (player == null || xpPoints <= 0)
            return Math.max(0, xpPoints);

        int remaining = xpPoints;
        PlayerInventory inv = player.getInventory();

        remaining = mendEquippedSlot(inv, inv.getItemInMainHand(), remaining, slot -> inv.setItemInMainHand(slot));
        if (remaining <= 0)
            return 0;

        remaining = mendEquippedSlot(inv, inv.getItemInOffHand(), remaining, slot -> inv.setItemInOffHand(slot));
        if (remaining <= 0)
            return 0;

        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length && remaining > 0; i++) {
            final int idx = i;
            remaining = mendEquippedSlot(inv, armor[i], remaining, repaired -> {
                ItemStack[] updated = inv.getArmorContents();
                if (idx >= 0 && idx < updated.length) {
                    updated[idx] = repaired;
                    inv.setArmorContents(updated);
                }
            });
        }

        return Math.max(0, remaining);
    }

    private int mendEquippedSlot(PlayerInventory inv, ItemStack item, int xpPoints,
            java.util.function.Consumer<ItemStack> applySlot) {
        if (xpPoints <= 0 || ItemStacks.isAir(item) || applySlot == null)
            return xpPoints;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg))
            return xpPoints;
        if (!meta.hasEnchant(Enchantment.MENDING))
            return xpPoints;

        int damage = Math.max(0, dmg.getDamage());
        if (damage <= 0)
            return xpPoints;

        int maxRepair = xpPoints * 2;
        int repairedDurability = Math.min(damage, maxRepair);
        if (repairedDurability <= 0)
            return xpPoints;

        int xpUsed = (repairedDurability + 1) / 2;
        dmg.setDamage(damage - repairedDurability);

        ItemStack clone = item.clone();
        clone.setItemMeta((ItemMeta) dmg);
        applySlot.accept(clone);

        return Math.max(0, xpPoints - xpUsed);
    }

    private String resolvePumpMode(BackpackData data, UUID moduleId, String upgradeId) {
        return resolvePumpMode(data, moduleId, upgradeId, false);
    }

    private String resolvePumpMode(BackpackData data, UUID moduleId, String upgradeId, boolean allowKeepLevel) {
        String fallback = plugin.getConfig().getString("Upgrades." + upgradeId + ".Mode", "Deposit");
        ItemStack moduleItem = resolveModuleSnapshotItem(data, moduleId);
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return normalizePumpMode(fallback, allowKeepLevel);

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return normalizePumpMode(fallback, allowKeepLevel);

        String raw = meta.getPersistentDataContainer().get(plugin.keys().MODULE_PUMP_MODE, PersistentDataType.STRING);
        return normalizePumpMode(raw == null || raw.isBlank() ? fallback : raw, allowKeepLevel);
    }

    private String normalizePumpMode(String raw, boolean allowKeepLevel) {
        if (raw == null)
            return "DEPOSIT";
        String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (allowKeepLevel && (s.contains("KEEP") || s.contains("LEVEL")))
            return "KEEP_LEVEL";
        if (s.contains("WITHDRAW") || s.contains("OUT"))
            return "WITHDRAW";
        return "DEPOSIT";
    }

    private boolean maintainPlayerLevel(Player player, BackpackData data, UUID expPumpModuleId,
            TankStateCodec.State state) {
        if (player == null || data == null || expPumpModuleId == null || state == null)
            return false;

        int targetLevel = resolveExpPumpTargetLevel(data, expPumpModuleId);
        int keepLevelStepBudget = resolveExpPumpKeepLevelStepBudgetPerCycle();
        if (keepLevelStepBudget <= 0)
            return false;

        // Move the player's displayed level toward target in discrete level steps.
        if (player.getLevel() > targetLevel) {
            int room = TankModuleLogic.MAX_EXP_POINTS - state.expTotalPoints;
            if (room <= 0)
                return false;

            int movedTotal = 0;
            for (int i = 0; i < keepLevelStepBudget && player.getLevel() > targetLevel; i++) {
                int pointsForOneLevel = pointsForOneDisplayedLevelLoss(player);
                if (pointsForOneLevel <= 0 || pointsForOneLevel > room)
                    break;

                player.giveExp(-pointsForOneLevel);
                state.expTotalPoints += pointsForOneLevel;
                room -= pointsForOneLevel;
                movedTotal += pointsForOneLevel;
            }

            return movedTotal > 0;
        }

        if (player.getLevel() < targetLevel) {
            int grantedTotal = 0;
            int available = state.expTotalPoints;
            for (int i = 0; i < keepLevelStepBudget && player.getLevel() < targetLevel; i++) {
                int pointsForOneLevel = pointsForOneDisplayedLevelGain(player);
                if (pointsForOneLevel <= 0 || pointsForOneLevel > available)
                    break;

                player.giveExp(pointsForOneLevel);
                available -= pointsForOneLevel;
                grantedTotal += pointsForOneLevel;
            }

            if (grantedTotal <= 0)
                return false;

            state.expTotalPoints -= grantedTotal;
            return true;
        }

        // When exactly at target level, optionally consume a small budget for passive
        // mending.
        if (!isExpPumpMendingEnabled(data, expPumpModuleId))
            return false;

        int repairDemand = estimateMendingXpDemand(player);
        if (repairDemand <= 0 || state.expTotalPoints <= 0)
            return false;

        int toMend = Math.min(repairDemand, Math.min(resolveExpPumpMendingBudgetPerCycle(), state.expTotalPoints));
        if (toMend <= 0)
            return false;

        int leftover = applyMendingToEquippedItems(player, toMend);
        int used = Math.max(0, toMend - leftover);
        if (used <= 0)
            return false;

        state.expTotalPoints -= used;

        return true;
    }

    private int resolveExpPumpTargetLevel(BackpackData data, UUID expPumpModuleId) {
        int fallback = plugin.getConfig().getInt("Upgrades.ExpPump.TargetLevel", 30);
        ItemStack moduleItem = resolveModuleSnapshotItem(data, expPumpModuleId);
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return clampExpPumpTargetLevel(fallback);

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return clampExpPumpTargetLevel(fallback);

        Integer stored = meta.getPersistentDataContainer().get(plugin.keys().MODULE_EXP_PUMP_TARGET_LEVEL,
                PersistentDataType.INTEGER);
        return clampExpPumpTargetLevel(stored == null ? fallback : stored.intValue());
    }

    private int clampExpPumpTargetLevel(int level) {
        return Math.max(0, Math.min(MAX_EXP_PUMP_TARGET_LEVEL, level));
    }

    private int estimateMendingXpDemand(Player player) {
        if (player == null)
            return 0;

        int total = 0;
        PlayerInventory inv = player.getInventory();
        total += estimateMendingXpDemand(inv.getItemInMainHand());
        total += estimateMendingXpDemand(inv.getItemInOffHand());
        for (ItemStack armor : inv.getArmorContents()) {
            total += estimateMendingXpDemand(armor);
        }
        return Math.max(0, total);
    }

    private int resolveExpPumpMendingBudgetPerCycle() {
        int fallback = 2;
        int configured = plugin.getConfig().getInt("Upgrades.ExpPump.MendingXpPerTick", fallback);
        return Math.max(1, Math.min(100, configured));
    }

    private int resolveExpPumpKeepLevelStepBudgetPerCycle() {
        int fallback = 1;
        int configured = plugin.getConfig().getInt("Upgrades.ExpPump.KeepLevelLevelsPerTick",
                plugin.getConfig().getInt("Upgrades.ExpPump.KeepLevelXpPerTick", fallback));
        return Math.max(1, Math.min(10, configured));
    }

    private int estimateMendingXpDemand(ItemStack item) {
        if (ItemStacks.isAir(item))
            return 0;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg))
            return 0;
        if (!meta.hasEnchant(Enchantment.MENDING))
            return 0;

        int damage = Math.max(0, dmg.getDamage());
        if (damage <= 0)
            return 0;

        return (damage + 1) / 2;
    }

    private UUID findFluidTankModuleId(BackpackData data) {
        UUID fluid = findInstalledModuleId(data, "FluidTank");
        if (fluid != null)
            return fluid;
        return findInstalledModuleId(data, "Tank");
    }

    private UUID findExperienceTankModuleId(BackpackData data) {
        UUID exp = findInstalledModuleId(data, "ExperienceTank");
        if (exp != null)
            return exp;
        return findInstalledModuleId(data, "Tank");
    }

    private boolean depositFluidFromInventory(Player player, TankStateCodec.State state) {
        if (player == null || state == null)
            return false;
        if (state.fluidBuckets >= TankModuleLogic.MAX_FLUID_BUCKETS)
            return false;

        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null)
            return false;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (ItemStacks.isAir(stack))
                continue;
            Material mat = stack.getType();
            if (!TankModuleLogic.isSupportedFluidBucket(mat))
                continue;
            if (state.fluidBuckets > 0 && state.fluidBucketMaterial != null
                    && !state.fluidBucketMaterial.equalsIgnoreCase(mat.name())) {
                continue;
            }

            ItemStack newStack = stack.clone();
            if (newStack.getAmount() <= 1) {
                player.getInventory().setItem(slot, new ItemStack(Material.BUCKET, 1));
            } else {
                newStack.setAmount(newStack.getAmount() - 1);
                player.getInventory().setItem(slot, newStack);
                var leftovers = player.getInventory().addItem(new ItemStack(Material.BUCKET, 1));
                if (!leftovers.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(Material.BUCKET, 1));
                }
            }

            state.fluidBucketMaterial = mat.name();
            state.fluidBuckets++;
            return true;
        }

        return false;
    }

    private boolean withdrawFluidToInventory(Player player, TankStateCodec.State state) {
        if (player == null || state == null)
            return false;
        if (state.fluidBuckets <= 0 || state.fluidBucketMaterial == null)
            return false;

        Material fluidBucket = Material.matchMaterial(state.fluidBucketMaterial);
        if (fluidBucket == null || !TankModuleLogic.isSupportedFluidBucket(fluidBucket))
            return false;

        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null)
            return false;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (ItemStacks.isAir(stack) || stack.getType() != Material.BUCKET)
                continue;

            ItemStack newStack = stack.clone();
            if (newStack.getAmount() <= 1) {
                player.getInventory().setItem(slot, new ItemStack(fluidBucket, 1));
            } else {
                newStack.setAmount(newStack.getAmount() - 1);
                player.getInventory().setItem(slot, newStack);
                var leftovers = player.getInventory().addItem(new ItemStack(fluidBucket, 1));
                if (!leftovers.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(fluidBucket, 1));
                }
            }

            state.fluidBuckets--;
            if (state.fluidBuckets <= 0) {
                state.fluidBuckets = 0;
                state.fluidBucketMaterial = null;
            }
            return true;
        }

        return false;
    }

    private void updateTankSnapshot(BackpackData data, UUID moduleId, TankStateCodec.State state) {
        ItemStack snapshot = resolveModuleSnapshotItem(data, moduleId);
        if (snapshot == null)
            return;

        byte[] encoded = TankStateCodec.encode(state);
        ItemMeta meta = snapshot.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(plugin.keys().MODULE_STATE_B64,
                    PersistentDataType.STRING,
                    java.util.Base64.getEncoder().encodeToString(encoded));
            snapshot.setItemMeta(meta);
        }

        ItemStack updated = TankModuleLogic.applyVisuals(plugin, snapshot, state);
        data.installedSnapshots().put(moduleId, ItemStackCodec.toBytes(new ItemStack[] { updated }));
    }

    private int pointsForOneDisplayedLevelGain(Player player) {
        if (player == null)
            return 0;

        int oldTotal = TankExperience.totalFromLevelAndProgress(player.getLevel(), player.getExp());
        int newTotal = TankExperience.totalFromLevelAndProgress(player.getLevel() + 1, player.getExp());
        return Math.max(0, newTotal - oldTotal);
    }

    private int pointsForOneDisplayedLevelLoss(Player player) {
        if (player == null || player.getLevel() <= 0)
            return 0;

        int oldTotal = TankExperience.totalFromLevelAndProgress(player.getLevel(), player.getExp());
        int newTotal = TankExperience.totalFromLevelAndProgress(player.getLevel() - 1, player.getExp());
        return Math.max(0, oldTotal - newTotal);
    }

    private ItemStack[] ensureCraftingInventorySize(ItemStack[] items) {
        int size = 10;
        if (items != null && items.length == size) {
            return items;
        }

        ItemStack[] normalized = new ItemStack[size];
        if (items != null && items.length > 0) {
            System.arraycopy(items, 0, normalized, 0, Math.min(items.length, size));
        }
        return normalized;
    }

    private int craftAutocraftingBatch(Player player, ItemStack[] moduleInv, ItemStack[] logical, int desiredAmount) {
        if (moduleInv == null || moduleInv.length < 10 || logical == null || desiredAmount <= 0) {
            return 0;
        }

        ItemStack[] template = buildAutocraftingTemplate(moduleInv);
        if (template == null) {
            return 0;
        }

        if (!canSatisfyTemplateOnce(template, logical)) {
            return 0;
        }

        Inventory virtual = Bukkit.createInventory(null, 18);

        int completedOperations = 0;
        for (int operations = 0; operations < 64 && completedOperations < desiredAmount; operations++) {
            virtual.clear();

            if (!hydrateAutocraftingMatrixFromLogical(template, logical, virtual)) {
                break;
            }

            ItemStack crafted = CraftingModuleLogic.craftOnce(plugin.recipes(), player, virtual,
                    candidate -> canInsertIntoLogical(logical, candidate));
            boolean leftoversStored = storeMatrixRemaindersInLogical(virtual, logical);
            if (ItemStacks.isAir(crafted)) {
                break;
            }
            if (!leftoversStored) {
                break;
            }

            ItemStack remainder = insertIntoLogical(logical, crafted);
            if (ItemStacks.isNotAir(remainder)) {
                break;
            }

            completedOperations++;
        }

        return completedOperations;
    }

    private boolean canSatisfyTemplateOnce(ItemStack[] template, ItemStack[] logical) {
        if (template == null || logical == null) {
            return false;
        }

        ItemStack[] remaining = new ItemStack[logical.length];
        for (int i = 0; i < logical.length; i++) {
            ItemStack slot = logical[i];
            if (ItemStacks.isNotAir(slot)) {
                remaining[i] = slot.clone();
            }
        }

        for (ItemStack marker : template) {
            if (ItemStacks.isAir(marker)) {
                continue;
            }

            boolean matched = false;
            for (int i = 0; i < remaining.length; i++) {
                ItemStack slot = remaining[i];
                if (ItemStacks.isAir(slot) || !slot.isSimilar(marker)) {
                    continue;
                }

                int nextAmount = slot.getAmount() - 1;
                if (nextAmount > 0) {
                    slot.setAmount(nextAmount);
                } else {
                    remaining[i] = null;
                }
                matched = true;
                break;
            }

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private ItemStack[] buildAutocraftingTemplate(ItemStack[] moduleInv) {
        if (moduleInv == null || moduleInv.length < 10) {
            return null;
        }

        ItemStack[] template = new ItemStack[9];
        boolean hasAny = false;
        for (int i = 0; i < 9; i++) {
            ItemStack src = moduleInv[i + 1];
            if (ItemStacks.isAir(src)) {
                continue;
            }

            ItemStack marker = src.clone();
            marker.setAmount(1);
            template[i] = marker;
            hasAny = true;
        }

        return hasAny ? template : null;
    }

    private boolean hydrateAutocraftingMatrixFromLogical(ItemStack[] template, ItemStack[] logical, Inventory working) {
        if (template == null || logical == null || working == null) {
            return false;
        }

        for (int i = 0; i < template.length; i++) {
            ItemStack marker = template[i];
            if (ItemStacks.isAir(marker)) {
                working.setItem(i + 1, null);
                continue;
            }

            ItemStack one = takeOneMatchingFromLogical(logical, marker);
            if (ItemStacks.isAir(one)) {
                storeMatrixRemaindersInLogical(working, logical);
                return false;
            }
            working.setItem(i + 1, one);
        }

        working.setItem(0, null);
        return true;
    }

    private ItemStack takeOneMatchingFromLogical(ItemStack[] logical, ItemStack marker) {
        if (logical == null || ItemStacks.isAir(marker)) {
            return null;
        }

        for (int i = 0; i < logical.length; i++) {
            ItemStack slot = logical[i];
            if (ItemStacks.isAir(slot) || !slot.isSimilar(marker)) {
                continue;
            }

            ItemStack out = slot.clone();
            out.setAmount(1);

            int next = slot.getAmount() - 1;
            if (next > 0) {
                slot.setAmount(next);
            } else {
                logical[i] = null;
            }

            return out;
        }

        return null;
    }

    private boolean storeMatrixRemaindersInLogical(Inventory working, ItemStack[] logical) {
        if (working == null || logical == null) {
            return false;
        }

        for (int i = 1; i <= 9; i++) {
            ItemStack slot = working.getItem(i);
            if (ItemStacks.isAir(slot)) {
                continue;
            }

            ItemStack remainder = insertIntoLogical(logical, slot.clone());
            if (ItemStacks.isNotAir(remainder)) {
                return false;
            }
            working.setItem(i, null);
        }

        working.setItem(0, null);
        return true;
    }

    private boolean canInsertIntoLogical(ItemStack[] logical, ItemStack stack) {
        if (ItemStacks.isAir(stack) || logical == null) {
            return true;
        }

        int needed = stack.getAmount();
        if (needed <= 0) {
            return true;
        }

        for (ItemStack slot : logical) {
            if (ItemStacks.isAir(slot)) {
                return true;
            }
            if (!slot.isSimilar(stack)) {
                continue;
            }

            int space = slot.getMaxStackSize() - slot.getAmount();
            if (space <= 0) {
                continue;
            }

            needed -= space;
            if (needed <= 0) {
                return true;
            }
        }

        return false;
    }

    private ItemStack insertIntoLogical(ItemStack[] logical, ItemStack stack) {
        if (ItemStacks.isAir(stack) || logical == null) {
            return null;
        }

        ItemStack moving = stack.clone();

        for (int i = 0; i < logical.length; i++) {
            ItemStack slot = logical[i];
            if (ItemStacks.isAir(slot) || !slot.isSimilar(moving)) {
                continue;
            }

            int maxStack = slot.getMaxStackSize();
            int space = maxStack - slot.getAmount();
            if (space <= 0) {
                continue;
            }

            int toMove = Math.min(space, moving.getAmount());
            slot.setAmount(slot.getAmount() + toMove);
            moving.setAmount(moving.getAmount() - toMove);
            if (moving.getAmount() <= 0) {
                return null;
            }
        }

        for (int i = 0; i < logical.length; i++) {
            if (ItemStacks.isNotAir(logical[i])) {
                continue;
            }
            logical[i] = moving.clone();
            return null;
        }

        return moving;
    }

    private void refreshBackpackItemsFor(Player player, UUID backpackId, BackpackTypeDef typeDef,
            BackpackData data) {
        if (player == null || backpackId == null || typeDef == null || data == null)
            return;

        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null || contents.length == 0)
            return;

        Keys keys = plugin.keys();
        int totalSlots = typeDef.rows() * 9;

        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            UUID id = readBackpackId(keys, it);
            if (id == null || !id.equals(backpackId))
                continue;

            if (backpackItems.refreshInPlace(it, typeDef, backpackId, data, totalSlots)) {
                player.getInventory().setItem(i, it);
            }
        }

        // setItem() sends slot updates; avoid updateInventory() here (engine runs
        // often)
    }

    private ItemStack[] ensureLogicalContentsSize(BackpackData data, int size) {
        if (size < 0)
            size = 0;
        ItemStack[] logical = ItemStackCodec.fromBytes(data.contentsBytes());
        if (logical.length != size) {
            ItemStack[] resized = new ItemStack[size];
            System.arraycopy(logical, 0, resized, 0, Math.min(logical.length, size));
            logical = resized;
        }
        return logical;
    }

    private UUID findInstalledModuleId(BackpackData data, String targetModuleType) {
        if (data == null || targetModuleType == null)
            return null;

        for (UUID moduleId : data.installedModules().values()) {
            if (moduleId == null)
                continue;
            ItemStack moduleItem = resolveModuleSnapshotItem(data, moduleId);
            if (moduleItem == null || !moduleItem.hasItemMeta())
                continue;

            ItemMeta meta = moduleItem.getItemMeta();
            if (meta == null)
                continue;

            String moduleType = meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE,
                    PersistentDataType.STRING);
            if (moduleType == null || !moduleType.equalsIgnoreCase(targetModuleType))
                continue;

            var def = plugin.cfg().findUpgrade(moduleType);
            if (def == null || !def.enabled())
                continue;

            if (def.toggleable()) {
                Byte enabled = meta.getPersistentDataContainer().get(plugin.keys().MODULE_ENABLED,
                        PersistentDataType.BYTE);
                if (enabled != null && enabled == 0)
                    continue;
            }

            return moduleId;
        }
        return null;
    }

    private ItemStack resolveModuleSnapshotItem(BackpackData data, UUID moduleId) {
        byte[] snap = data.installedSnapshots().get(moduleId);
        if (snap == null)
            return null;
        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(snap);
        } catch (Exception ignored) {
            return null;
        }
        if (arr.length == 0)
            return null;
        return arr[0];
    }

    private Set<Material> readWhitelistFromState(BackpackData data, UUID moduleId) {
        if (data == null || moduleId == null)
            return Set.of();
        byte[] bytes = data.moduleStates().get(moduleId);
        if (bytes == null || bytes.length == 0)
            return Set.of();

        ItemStack[] arr = ItemStackCodec.fromBytes(bytes);
        if (arr == null || arr.length == 0)
            return Set.of();

        Set<Material> out = new LinkedHashSet<>();
        for (ItemStack it : arr) {
            if (ItemStacks.isAir(it))
                continue;
            out.add(it.getType());
        }
        return out;
    }

    private List<Material> readWhitelistOrderedFromState(BackpackData data, UUID moduleId) {
        if (data == null || moduleId == null)
            return java.util.Collections.emptyList();
        byte[] bytes = data.moduleStates().get(moduleId);
        if (bytes == null || bytes.length == 0)
            return java.util.Collections.emptyList();

        ItemStack[] arr = ItemStackCodec.fromBytes(bytes);
        if (arr == null || arr.length == 0)
            return java.util.Collections.emptyList();

        java.util.LinkedHashSet<Material> seen = new java.util.LinkedHashSet<>();
        for (ItemStack it : arr) {
            if (ItemStacks.isAir(it))
                continue;
            seen.add(it.getType());
        }
        return new java.util.ArrayList<>(seen);
    }

    private int readRestockThresholdFromState(BackpackData data, UUID moduleId) {
        if (data == null || moduleId == null)
            return RestockEngine.clampThreshold(0);
        byte[] bytes = data.moduleStates().get(moduleId);
        if (bytes == null || bytes.length == 0)
            return RestockEngine.clampThreshold(0);

        ItemStack[] arr = ItemStackCodec.fromBytes(bytes);
        if (arr == null || arr.length == 0)
            return RestockEngine.clampThreshold(0);

        // Prefer merged-state index 9 (whitelist[0..8] + threshold[9]).
        if (arr.length > 9 && ItemStacks.isNotAir(arr[9])) {
            return RestockEngine.clampThreshold(arr[9].getAmount());
        }

        // Back-compat (old hopper-only): slot 2 is the center.
        if (arr.length > 2 && ItemStacks.isNotAir(arr[2])) {
            return RestockEngine.clampThreshold(arr[2].getAmount());
        }

        return RestockEngine.clampThreshold(0);
    }

    private java.util.List<ItemStack> readRestockWhitelistFromState(BackpackData data, UUID moduleId) {
        if (data == null || moduleId == null)
            return java.util.Collections.emptyList();
        byte[] bytes = data.moduleStates().get(moduleId);
        if (bytes == null || bytes.length == 0)
            return java.util.Collections.emptyList();

        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(bytes);
        } catch (Exception ex) {
            return java.util.Collections.emptyList();
        }
        if (arr == null || arr.length == 0)
            return java.util.Collections.emptyList();

        int limit = Math.min(9, arr.length);
        java.util.ArrayList<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ItemStack it = arr[i];
            if (ItemStacks.isAir(it))
                continue;
            ItemStack s = it.clone();
            s.setAmount(1);
            out.add(s);
        }
        return out;
    }

    private static UUID readBackpackId(Keys keys, ItemStack item) {
        if (ItemStacks.isAir(item) || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        String idStr = meta.getPersistentDataContainer().get(keys.BACKPACK_ID, PersistentDataType.STRING);
        if (idStr == null || idStr.isBlank())
            return null;
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String readBackpackType(Keys keys, ItemStack item) {
        if (ItemStacks.isAir(item) || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        return meta.getPersistentDataContainer().get(keys.BACKPACK_TYPE, PersistentDataType.STRING);
    }
}
