package io.github.tootertutor.ModularPacks.modules;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
import io.github.tootertutor.ModularPacks.util.ItemStacks;

/**
 * Periodically ticks open module screens and passive backpack modules.
 * Mutation-sensitive logic is skipped while the affected backpack GUI is open.
 */
public final class ModuleEngineService {

    private static final long ENGINE_PERIOD_TICKS = 10L;
    private static final int ENGINE_DT_TICKS = 10;

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;
    private final FeedingEngine feedingEngine;
    private final JukeboxEngine jukeboxEngine;
    private final MagnetVoidEngine magnetVoidEngine;
    private final FurnaceEngine furnaceEngine;
    private final RestockEngine restockEngine;
    private final ScreenRouter screenRouter;
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

            Byte enabled = meta.getPersistentDataContainer().get(plugin.keys().MODULE_ENABLED, PersistentDataType.BYTE);
            if (enabled != null && enabled == 0)
                continue;

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
