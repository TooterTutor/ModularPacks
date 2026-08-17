package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

/**
 * Resolves carried, enabled Quivers in deterministic inventory/socket order.
 */
public final class QuiverSourceResolver {

    private final ModularPacksPlugin plugin;
    private final QuiverAmmoService ammoService;
    private final QuiverSelectionCodec selectionCodec;

    public QuiverSourceResolver(ModularPacksPlugin plugin, QuiverAmmoService ammoService,
            QuiverSelectionCodec selectionCodec) {
        this.plugin = plugin;
        this.ammoService = ammoService;
        this.selectionCodec = selectionCodec;
    }

    public QuiverSource resolve(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack[] carried = player.getInventory().getContents();
        for (ItemStack item : carried) {
            BackpackReference reference = readBackpack(item);
            if (reference == null) {
                continue;
            }
            UUID lockedViewer = plugin.sessions().lockedViewerInGroup(reference.backpackId());
            if (lockedViewer != null && !lockedViewer.equals(player.getUniqueId())) {
                continue;
            }

            BackpackData data = plugin.repo().loadOrCreate(reference.backpackId(), reference.backpackType());
            var type = plugin.cfg().findType(data.backpackType());
            if (type == null) {
                continue;
            }
            BackpackStorage storage = plugin.backpackStorage().load(data, type.rows() * 9);
            for (Map.Entry<Integer, UUID> installed : installedInSocketOrder(data)) {
                UUID moduleId = installed.getValue();
                if (!isEnabledQuiver(data, moduleId)) {
                    continue;
                }
                QuiverSelection selection = selectionCodec.decode(data.moduleStates().get(moduleId));
                ItemStack selected = ammoService.select(selection, storage);
                if (selected != null) {
                    return new QuiverSource(reference.backpackId(), reference.backpackType(), moduleId,
                            selection, selected);
                }
            }
        }
        return null;
    }

    /**
     * Revalidates and consumes exactly one arrow synchronously on the server
     * thread.
     */
    public boolean consumeOne(Player player, QuiverSource source) {
        return mutateSource(player, source, true);
    }

    public boolean isAvailable(Player player, QuiverSource source) {
        return mutateSource(player, source, false);
    }

    private boolean mutateSource(Player player, QuiverSource source, boolean consume) {
        if (player == null || source == null) {
            return false;
        }
        if (!isCarriedBackpack(player, source.backpackId())) {
            return false;
        }
        UUID lockedViewer = plugin.sessions().lockedViewerInGroup(source.backpackId());
        if (lockedViewer != null && !lockedViewer.equals(player.getUniqueId())) {
            return false;
        }

        BackpackData data = plugin.repo().loadOrCreate(source.backpackId(), source.backpackType());
        if (!data.installedModules().containsValue(source.moduleId()) || !isEnabledQuiver(data, source.moduleId())) {
            return false;
        }

        QuiverSelection currentSelection = selectionCodec.decode(data.moduleStates().get(source.moduleId()));
        if (currentSelection.mode() == QuiverSelectionMode.EXACT) {
            ItemStack currentExact = currentSelection.selectedPrototype();
            if (currentExact == null
                    || !plugin.backpackStorage().identity().sameIdentity(currentExact, source.prototype())) {
                return false;
            }
        }

        var type = plugin.cfg().findType(data.backpackType());
        if (type == null) {
            return false;
        }
        BackpackStorage storage = plugin.backpackStorage().load(data, type.rows() * 9);
        if (ammoService.count(storage, source.prototype()) <= 0) {
            return false;
        }

        if (!consume) {
            return true;
        }
        if (!ammoService.consumeOne(storage, source.prototype())) {
            return false;
        }

        plugin.backpackStorage().save(data, storage);
        plugin.repo().saveBackpack(data);
        plugin.sessions().refreshLinkedBackpacksThrottled(source.backpackId(), data);
        debug("logical ammo committed for backpack=" + source.backpackId() + " module=" + source.moduleId());
        return true;
    }

    public QuiverSelection readSelection(BackpackData data, UUID moduleId) {
        return selectionCodec.decode(data == null ? null : data.moduleStates().get(moduleId));
    }

    public boolean saveSelection(UUID backpackId, String backpackType, UUID moduleId, QuiverSelection selection) {
        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
        if (!data.installedModules().containsValue(moduleId) || !isEnabledQuiverDefinition(data, moduleId)) {
            return false;
        }
        data.moduleStates().put(moduleId, selectionCodec.encode(selection));
        plugin.repo().saveBackpack(data);
        plugin.sessions().refreshLinkedBackpacksThrottled(backpackId, data);
        return true;
    }

    public boolean isEnabledQuiver(BackpackData data, UUID moduleId) {
        ItemStack snapshot = moduleSnapshot(data, moduleId);
        if (snapshot == null || !snapshot.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = snapshot.getItemMeta();
        String moduleType = meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE,
                PersistentDataType.STRING);
        if (moduleType == null || !moduleType.equalsIgnoreCase("Quiver")) {
            return false;
        }
        var definition = plugin.cfg().findUpgrade(moduleType);
        if (definition == null || !definition.enabled()) {
            return false;
        }
        Byte enabled = meta.getPersistentDataContainer().get(plugin.keys().MODULE_ENABLED, PersistentDataType.BYTE);
        return !definition.toggleable() || enabled == null || enabled != 0;
    }

    private boolean isEnabledQuiverDefinition(BackpackData data, UUID moduleId) {
        ItemStack snapshot = moduleSnapshot(data, moduleId);
        if (snapshot == null || !snapshot.hasItemMeta()) {
            return false;
        }
        String moduleType = snapshot.getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        return moduleType != null && moduleType.equalsIgnoreCase("Quiver")
                && plugin.cfg().findUpgrade(moduleType) != null;
    }

    private List<Map.Entry<Integer, UUID>> installedInSocketOrder(BackpackData data) {
        List<Map.Entry<Integer, UUID>> entries = new ArrayList<>(data.installedModules().entrySet());
        entries.sort(Comparator.comparingInt(entry -> entry.getKey()));
        return entries;
    }

    private ItemStack moduleSnapshot(BackpackData data, UUID moduleId) {
        byte[] bytes = data.installedSnapshots().get(moduleId);
        if (bytes == null) {
            return null;
        }
        try {
            ItemStack[] decoded = ItemStackCodec.fromBytes(bytes);
            return decoded.length == 0 ? null : decoded[0];
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private BackpackReference readBackpack(ItemStack item) {
        if (ItemStacks.isAir(item) || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING);
        String type = meta.getPersistentDataContainer().get(plugin.keys().BACKPACK_TYPE, PersistentDataType.STRING);
        if (id == null || type == null || type.isBlank()) {
            return null;
        }
        try {
            return new BackpackReference(UUID.fromString(id), type);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isCarriedBackpack(Player player, UUID backpackId) {
        for (ItemStack item : player.getInventory().getContents()) {
            BackpackReference reference = readBackpack(item);
            if (reference != null && reference.backpackId().equals(backpackId)) {
                return true;
            }
        }
        return false;
    }

    private void debug(String message) {
        if (plugin.cfg().debugClickLog()) {
            plugin.getLogger().info("[Quiver] " + message);
        }
    }

    private record BackpackReference(UUID backpackId, String backpackType) {
    }
}
