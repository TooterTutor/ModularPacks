package io.github.tootertutor.ModularPacks.item;

import java.util.Arrays;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Stores the full equipped item alongside the player's inventory in player
 * data.
 */
public final class BackpackBackSlot {
    private final ModularPacksPlugin plugin;
    private final BackpackItems items;
    private final NamespacedKey key;

    public BackpackBackSlot(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.items = new BackpackItems(plugin);
        this.key = new NamespacedKey(plugin, "back_slot_item");
    }

    public ItemStack get(Player player) {
        byte[] bytes = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        return bytes == null ? null : ItemStack.deserializeBytes(bytes);
    }

    public boolean matches(ItemStack item, UUID id) {
        return id != null && items.isBackpack(item) && id.toString().equals(item.getItemMeta()
                .getPersistentDataContainer().get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING));
    }

    public boolean isEquipped(Player player, UUID id) {
        return matches(get(player), id);
    }

    public ItemStack[] carriedItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack equipped = get(player);
        if (equipped == null) {
            return contents;
        }
        ItemStack[] carried = Arrays.copyOf(contents, contents.length + 1);
        carried[contents.length] = equipped;
        return carried;
    }

    public void update(Player player, ItemStack item) {
        ItemStack previous = get(player);
        if (previous != null && items.isBackpack(item)
                && previous.getItemMeta().getPersistentDataContainer().get(plugin.keys().BACKPACK_ID,
                        PersistentDataType.STRING).equals(
                                item.getItemMeta().getPersistentDataContainer()
                                        .get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING))) {
            store(player, item);
        }
    }

    public void clear(Player player) {
        player.getPersistentDataContainer().remove(key);
    }

    private void store(Player player, ItemStack item) {
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
    }

    public boolean toggle(Player player, UUID id) {
        PlayerInventory inventory = player.getInventory();
        ItemStack equipped = get(player);
        if (equipped != null && !matches(equipped, id)) {
            player.sendMessage(Text.c("&cUnequip your current backpack before equipping another."));
            return false;
        }
        int slot = -1;
        if (equipped != null) {
            slot = inventory.firstEmpty();
            if (slot < 0) {
                player.sendMessage(Text.c("&cMake room in your inventory before unequipping this backpack."));
                return false;
            }
        } else {
            for (int i = 0; i < inventory.getSize(); i++) {
                if (matches(inventory.getItem(i), id)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                player.sendMessage(Text.c("&cCould not find backpack item in inventory."));
                return false;
            }
            if (inventory.getItem(slot).getAmount() != 1) {
                player.sendMessage(Text.c("&cSeparate this backpack into a single item before equipping it."));
                return false;
            }
        }

        ItemStack previous = inventory.getItem(slot);
        try {
            if (equipped == null) {
                store(player, previous);
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, equipped);
                clear(player);
            }
            // Both the virtual slot and inventory are committed in the same player file.
            player.saveData();
            return true;
        } catch (RuntimeException ex) {
            inventory.setItem(slot, previous);
            if (equipped == null) {
                clear(player);
            } else {
                store(player, equipped);
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save backpack back slot", ex);
            player.sendMessage(Text.c("&cCould not save your backpack equipment. Please try again."));
            return false;
        }
    }
}
