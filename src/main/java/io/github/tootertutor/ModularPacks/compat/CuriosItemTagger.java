package io.github.tootertutor.ModularPacks.compat;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;

public final class CuriosItemTagger {

    private static final String CURIOS_PLUGIN_NAME = "CuriosPaper";
    private static final NamespacedKey CURIOS_ID_TAG = new NamespacedKey("curiospaper", "curios_custom_id");
    private static final NamespacedKey CURIOS_SLOT_TAG = new NamespacedKey("curiospaper", "curios_slot_type");

    private CuriosItemTagger() {
    }

    public static void syncBackpackTags(ModularPacksPlugin plugin, ItemMeta meta) {
        if (meta == null) {
            return;
        }

        if (shouldTagBackpacks(plugin)) {
            meta.getPersistentDataContainer().set(CURIOS_ID_TAG, PersistentDataType.STRING, "backpack");
            meta.getPersistentDataContainer().set(CURIOS_SLOT_TAG, PersistentDataType.STRING, "back");
            return;
        }

        meta.getPersistentDataContainer().remove(CURIOS_ID_TAG);
        meta.getPersistentDataContainer().remove(CURIOS_SLOT_TAG);
    }

    public static void syncBackpackTags(ModularPacksPlugin plugin, ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        syncBackpackTags(plugin, meta);
        item.setItemMeta(meta);
    }

    public static void removeBackpackTags(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(CURIOS_ID_TAG);
        meta.getPersistentDataContainer().remove(CURIOS_SLOT_TAG);
        item.setItemMeta(meta);
    }

    public static boolean shouldTagBackpacks(ModularPacksPlugin plugin) {
        return plugin != null
                && plugin.cfg() != null
                && plugin.cfg().curiosIntegrationEnabled()
                && Bukkit.getPluginManager().isPluginEnabled(CURIOS_PLUGIN_NAME);
    }
}
