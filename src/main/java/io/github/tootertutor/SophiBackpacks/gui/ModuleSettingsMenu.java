package io.github.tootertutor.SophiBackpacks.gui;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;
import io.github.tootertutor.SophiBackpacks.text.Text;

public final class ModuleSettingsMenu {
    private final SophiBackpacksPlugin plugin;

    public ModuleSettingsMenu(SophiBackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, BackpackMenuHolder backpackHolder, UUID moduleId) {
        ModuleSettingsHolder holder = new ModuleSettingsHolder(backpackHolder.backpackId(), moduleId);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c("&8Module Filters"));
        holder.setInventory(inv);

        render(inv, backpackHolder, moduleId);
        player.openInventory(inv);
    }

    public void render(Inventory inv, BackpackMenuHolder backpackHolder, UUID moduleId) {
        inv.clear();

        ItemStack moduleItem = getModuleSnapshotItem(backpackHolder, moduleId);
        if (moduleItem == null) {
            inv.setItem(13, named(Material.BARRIER, "&cMissing Module"));
            return;
        }

        FilterData data = readFilter(moduleItem);

        // display filter materials
        int i = 0;
        for (Material m : data.materials) {
            if (i >= 18)
                break;
            inv.setItem(i++, new ItemStack(m));
        }

        inv.setItem(18, named(Material.REPEATER, "&eMode: &f" + data.mode));
        inv.setItem(22, named(Material.HOPPER, "&aAdd from cursor"));
        inv.setItem(26, named(Material.ARROW, "&7Back"));
    }

    private ItemStack getModuleSnapshotItem(BackpackMenuHolder holder, UUID moduleId) {
        byte[] snap = holder.data().installedSnapshots().get(moduleId);
        if (snap == null)
            return null;
        ItemStack[] arr = io.github.tootertutor.SophiBackpacks.data.ItemStackCodec.fromBytes(snap);
        return arr.length > 0 ? arr[0] : null;
    }

    public record FilterData(String mode, Set<Material> materials) {
    }

    public FilterData readFilter(ItemStack moduleItem) {
        var keys = plugin.keys();
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return new FilterData("WHITELIST", new LinkedHashSet<>());

        String mode = meta.getPersistentDataContainer().get(keys.MODULE_FILTER_MODE, PersistentDataType.STRING);
        if (mode == null)
            mode = "WHITELIST";

        String raw = meta.getPersistentDataContainer().get(keys.MODULE_FILTER_LIST, PersistentDataType.STRING);
        Set<Material> mats = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                Material m = Material.matchMaterial(part.trim());
                if (m != null)
                    mats.add(m);
            }
        }
        return new FilterData(mode, mats);
    }

    public void writeFilter(ItemStack moduleItem, FilterData data) {
        var keys = plugin.keys();
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return;

        meta.getPersistentDataContainer().set(keys.MODULE_FILTER_MODE, PersistentDataType.STRING, data.mode);
        String joined = String.join(",", data.materials.stream().map(Material::name).toList());
        meta.getPersistentDataContainer().set(keys.MODULE_FILTER_LIST, PersistentDataType.STRING, joined);
        moduleItem.setItemMeta(meta);
    }

    private ItemStack named(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            it.setItemMeta(meta);
        }
        return it;
    }
}
