package io.github.tootertutor.ModularPacks.item;

import java.util.List;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.config.Placeholders;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;

public final class BackpackItems {
    private static final NamespacedKey VIRTUAL_BACKPACK_ITEM_MODEL = NamespacedKey.minecraft("air");

    private final ModularPacksPlugin plugin;

    public BackpackItems(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(String typeId) {
        BackpackTypeDef type = plugin.cfg().findType(typeId);
        if (type == null)
            throw new IllegalArgumentException("Unknown backpack type: " + typeId);

        UUID id = UUID.randomUUID();
        return createExisting(id, typeId);
    }

    public ItemStack createExisting(UUID id, String typeId) {
        if (id == null)
            throw new IllegalArgumentException("id cannot be null");

        BackpackTypeDef type = plugin.cfg().findType(typeId);
        if (type == null)
            throw new IllegalArgumentException("Unknown backpack type: " + typeId);

        ItemStack item = new ItemStack(type.outputMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.displayName(Text.c(type.displayName()));
        meta.getPersistentDataContainer().set(plugin.keys().BACKPACK_ID, PersistentDataType.STRING, id.toString());
        meta.getPersistentDataContainer().set(plugin.keys().BACKPACK_TYPE, PersistentDataType.STRING, type.id());

        if (type.customModelData() > 0) {
            CustomModelDataUtil.setCustomModelData(meta, type.customModelData());
        }

        if (meta instanceof SkullMeta skull && type.skullData() != null) {
            SkullTextureUtil.applyBase64Texture(skull, type.skullData());
        }

        List<String> lore = type.lore();
        if (lore != null && !lore.isEmpty()) {
            List<String> expanded = Placeholders.expandBackpackLore(plugin, type, id, lore);
            meta.lore(Text.lore(expanded));
        }

        // Load backpack data and add module-based CMD strings
        BackpackData backpackData = plugin.repo().loadOrCreate(id, typeId);
        List<String> moduleModelDataStrings = ModuleModelDataGenerator.generateModuleModelDataStrings(plugin,
                backpackData);

        if (!moduleModelDataStrings.isEmpty()) {
            CustomModelDataUtil.setCustomModelDataStrings(meta, moduleModelDataStrings);
        } else {
            CustomModelDataUtil.setCustomModelDataStrings(meta, List.of());
        }

        // Slot strings are authoritative for module medallion placement.
        // Clear flags to avoid stale type-indexed rendering behavior.
        CustomModelDataUtil.setCustomModelDataFlags(meta, List.of());
        applyWearableComponents(meta);

        item.setItemMeta(meta);
        return item;
    }

    public boolean refreshInPlace(ItemStack item, BackpackTypeDef type, UUID backpackId, BackpackData data,
            int totalSlots) {
        if (ItemStacks.isAir(item) || type == null || backpackId == null)
            return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;

        String customName = data != null && data.backpackName() != null ? data.backpackName().trim() : "";
        meta.displayName(Text.c(customName.isEmpty() ? type.displayName() : customName));
        meta.getPersistentDataContainer().set(plugin.keys().BACKPACK_ID, PersistentDataType.STRING,
                backpackId.toString());
        meta.getPersistentDataContainer().set(plugin.keys().BACKPACK_TYPE, PersistentDataType.STRING, type.id());

        if (type.customModelData() > 0) {
            CustomModelDataUtil.setCustomModelData(meta, type.customModelData());
        } else if (meta.hasCustomModelDataComponent()) {
            CustomModelDataUtil.setCustomModelData(meta, null);
        }

        if (meta instanceof SkullMeta skull && type.skullData() != null) {
            SkullTextureUtil.applyBase64Texture(skull, type.skullData());
        }

        List<String> lore = type.lore();
        if (lore != null && !lore.isEmpty()) {
            List<String> expanded = Placeholders.expandBackpackLore(plugin, type, backpackId, data, totalSlots, lore);
            meta.lore(Text.lore(expanded));
        } else {
            meta.lore(null);
        }

        // Generate module-based CMD strings (color info is in the colors array)
        List<String> moduleModelDataStrings = ModuleModelDataGenerator.generateModuleModelDataStrings(plugin, data);

        if (!moduleModelDataStrings.isEmpty()) {
            CustomModelDataUtil.setCustomModelDataStrings(meta, moduleModelDataStrings);
        } else {
            CustomModelDataUtil.setCustomModelDataStrings(meta, List.of());
        }

        // Slot strings are authoritative for module medallion placement.
        // Clear flags to avoid stale type-indexed rendering behavior.
        CustomModelDataUtil.setCustomModelDataFlags(meta, List.of());
        applyWearableComponents(meta);

        item.setItemMeta(meta);
        return true;
    }

    public boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.keys().BACKPACK_ID, PersistentDataType.STRING)
                && pdc.has(plugin.keys().BACKPACK_TYPE, PersistentDataType.STRING);
    }

    public ItemStack createWearableModel(ItemStack sourceBackpack) {
        if (!isBackpack(sourceBackpack))
            return new ItemStack(Material.AIR);

        ItemMeta sourceMeta = sourceBackpack.getItemMeta();
        if (sourceMeta == null)
            return new ItemStack(Material.AIR);

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return new ItemStack(Material.AIR);

        // The stand render item must carry only visual model components.
        int customModelData = CustomModelDataUtil.getCustomModelData(sourceMeta);
        if (customModelData > 0) {
            CustomModelDataUtil.setCustomModelData(meta, customModelData);
        }

        List<String> cmdStrings = CustomModelDataUtil.getCustomModelDataStrings(sourceMeta);
        if (!cmdStrings.isEmpty()) {
            CustomModelDataUtil.setCustomModelDataStrings(meta, cmdStrings);
        }

        List<Boolean> cmdFlags = CustomModelDataUtil.getCustomModelDataFlags(sourceMeta);
        if (!cmdFlags.isEmpty()) {
            CustomModelDataUtil.setCustomModelDataFlags(meta, cmdFlags);
        }

        List<Color> cmdColors = CustomModelDataUtil.getCustomModelDataColors(sourceMeta);
        if (!cmdColors.isEmpty()) {
            CustomModelDataUtil.setCustomModelDataColors(meta, cmdColors);
        }

        meta.setItemModel(VIRTUAL_BACKPACK_ITEM_MODEL);

        item.setItemMeta(meta);
        return item;
    }

    public boolean ensureWearableTag(ItemStack item) {
        if (!isBackpack(item))
            return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;

        boolean changed = false;
        if (meta.hasEquippable() && meta.getEquippable() != null) {
            EquippableComponent equippable = meta.getEquippable();
            equippable.setSlot(EquipmentSlot.CHEST);
            equippable.setEquipSound(Sound.ITEM_BUNDLE_DROP_CONTENTS);
            equippable.setEquipOnInteract(true);
            equippable.setSwappable(true);
            equippable.setAllowedEntities(EntityType.PLAYER);
            meta.setEquippable(equippable);
            changed = true;
        }

        if (item.getItemMeta() != meta) {
            item.setItemMeta(meta);
            changed = true;
        }

        return changed;
    }

    private void applyWearableComponents(ItemMeta meta) {
        if (meta == null)
            return;

        EquippableComponent equippable = meta.getEquippable();
        if (equippable == null)
            return;

        equippable.setSlot(EquipmentSlot.CHEST);
        equippable.setEquipSound(Sound.ITEM_BUNDLE_DROP_CONTENTS);
        equippable.setEquipOnInteract(true);
        equippable.setSwappable(true);
        equippable.setAllowedEntities(EntityType.PLAYER);
        meta.setEquippable(equippable);
    }
}