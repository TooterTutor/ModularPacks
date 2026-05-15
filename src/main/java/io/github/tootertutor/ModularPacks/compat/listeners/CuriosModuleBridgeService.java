package io.github.tootertutor.ModularPacks.compat.listeners;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.compat.CuriosCompat;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;

/**
 * Bridges Curios-equipped backpacks into ModularPacks ticking and item refresh.
 */
public final class CuriosModuleBridgeService {

    private static final String CURIOS_BACK_SLOT = "back";
    private static final long TICK_PERIOD = 10L;

    private final ModularPacksPlugin plugin;
    private final CuriosCompat curiosCompat;
    private final BackpackItems backpackItems;
    private BukkitTask task;

    public CuriosModuleBridgeService(ModularPacksPlugin plugin, CuriosCompat curiosCompat) {
        this.plugin = plugin;
        this.curiosCompat = curiosCompat;
        this.backpackItems = new BackpackItems(plugin);
    }

    public void start() {
        if (task != null) {
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBridge, TICK_PERIOD, TICK_PERIOD);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickBridge() {
        try {
            if (!curiosCompat.isApiReady() || plugin.cfg() == null || !plugin.cfg().curiosIntegrationEnabled()) {
                return;
            }

            Keys keys = plugin.keys();
            Set<UUID> openModuleIds = new HashSet<>();
            Set<UUID> openBackpackIds = collectOpenBackpackIds();

            for (Player player : Bukkit.getOnlinePlayers()) {
                processPlayer(player, keys, openModuleIds, openBackpackIds);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Curios module bridge tick failed: " + e.getMessage());
        }
    }

    private Set<UUID> collectOpenBackpackIds() {
        Set<UUID> openBackpackIds = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
                continue;
            }

            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BackpackMenuHolder holder) {
                openBackpackIds.add(holder.backpackId());
            }
        }
        return openBackpackIds;
    }

    private void processPlayer(Player player, Keys keys, Set<UUID> openModuleIds, Set<UUID> openBackpackIds) {
        List<ItemStack> equippedItems = curiosCompat.getEquippedItems(player, CURIOS_BACK_SLOT);
        if (equippedItems == null || equippedItems.isEmpty()) {
            return;
        }

        Set<UUID> inventoryBackpackIds = collectInventoryBackpackIds(player, keys);
        Set<UUID> processedBackpacks = new HashSet<>();

        for (int slotIndex = 0; slotIndex < equippedItems.size(); slotIndex++) {
            ItemStack item = equippedItems.get(slotIndex);
            UUID backpackId = readBackpackId(keys, item);
            if (backpackId == null) {
                continue;
            }

            String backpackType = readBackpackType(keys, item);
            if (backpackType == null || backpackType.isBlank()) {
                continue;
            }

            if (processedBackpacks.add(backpackId) && !inventoryBackpackIds.contains(backpackId)) {
                plugin.engines().tickBackpackFromBridge(player, backpackId, backpackType, openModuleIds,
                        openBackpackIds);
            }

            refreshEquippedBackpackItem(player, slotIndex, item, backpackId, backpackType);
        }
    }

    private void refreshEquippedBackpackItem(Player player, int slotIndex, ItemStack equippedItem, UUID backpackId,
            String backpackType) {
        BackpackTypeDef typeDef = plugin.cfg().findType(backpackType);
        if (typeDef == null) {
            return;
        }

        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
        if (data == null) {
            return;
        }

        int totalSlots = typeDef.rows() * 9;
        if (!backpackItems.refreshInPlace(equippedItem, typeDef, backpackId, data, totalSlots)) {
            return;
        }

        curiosCompat.setEquippedItem(player, CURIOS_BACK_SLOT, slotIndex, equippedItem);
    }

    private Set<UUID> collectInventoryBackpackIds(Player player, Keys keys) {
        Set<UUID> ids = new HashSet<>();
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null || contents.length == 0) {
            return ids;
        }

        for (ItemStack item : contents) {
            UUID id = readBackpackId(keys, item);
            if (id != null) {
                ids.add(id);
            }
        }

        return ids;
    }

    private UUID readBackpackId(Keys keys, ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        String id = item.getItemMeta().getPersistentDataContainer().get(keys.BACKPACK_ID, PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String readBackpackType(Keys keys, ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        return item.getItemMeta().getPersistentDataContainer().get(keys.BACKPACK_TYPE, PersistentDataType.STRING);
    }
}
