package io.github.tootertutor.ModularPacks.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.PlacedBackpack;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.CustomModelDataUtil;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackSharingHost;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackSharingJoiner;
import io.github.tootertutor.ModularPacks.util.BackpackColorTints;
import io.github.tootertutor.ModularPacks.util.Text;
import net.wesjd.anvilgui.AnvilGUI;

/**
 * Settings menu for backpacks.
 * Provides options to:
 * - Toggle Host/Join/Private modes
 * - Change backpack color (via CustomModelData tints)
 * - Set custom backpack name
 */
public final class BackpackSettingsMenu {

    private static final int SLOT_RESOURCE_PACKS = 2;
    private static final int SLOT_EQUIP = 6;
    private static final int SLOT_SHARING = 11;
    private static final int SLOT_COLORS = 13;
    private static final int SLOT_NAME = 15;
    private static final int SLOT_BACK = 22;
    private static final String[] COLOR_GROUP_NAMES = {
            "Body",
            "Stripes",
            "Main Pocket",
            "Side Pockets",
            "Towel"
    };

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;
    private final BackpackSharingHost sharingHost;
    private final BackpackSharingJoiner sharingJoiner;

    public BackpackSettingsMenu(ModularPacksPlugin plugin,
            BackpackSharingHost sharingHost, BackpackSharingJoiner sharingJoiner) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
        this.sharingHost = sharingHost;
        this.sharingJoiner = sharingJoiner;
    }

    /**
     * Open the settings menu for a backpack.
     */
    public void openSettingsMenu(Player player, BackpackMenuHolder holder) {
        BackpackData data = holder.data();
        SettingsMenuHolder settingsHolder = new SettingsMenuHolder(holder.backpackId(), holder);
        Inventory inv = plugin.getServer().createInventory(settingsHolder, 27,
                Text.c(plugin.lang().text(player, "gui.settings.title")));

        // Get the backpack item from player's inventory
        ItemStack backpackItem = resolveBackpackItemForSettings(player, holder);
        if (backpackItem == null) {
            player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.missingItem")));
            return;
        }

        // Resource packs button (top row, left)
        if (plugin.cfg().resourcePackEnabled()) {
            inv.setItem(SLOT_RESOURCE_PACKS,
                    createButton(plugin.lang().text(player, "gui.settings.resourcePacks"), Material.PAINTING,
                            List.of(plugin.lang().text(player, "gui.settings.packsDescription"),
                                    plugin.lang().text(player, "gui.settings.packsStaged"),
                                    "&7", plugin.lang().text(player, "gui.settings.packsOpen"))));
        }

        if (!holder.isPlacedContext()) {
            boolean equipped = plugin.modelManager().backSlot().isEquipped(player, holder.backpackId());
            inv.setItem(SLOT_EQUIP,
                    createButton(
                            equipped ? plugin.lang().text(player, "gui.settings.unequip")
                                    : plugin.lang().text(player, "gui.settings.equip"),
                            Material.DIAMOND_CHESTPLATE,
                            List.of(equipped ? plugin.lang().text(player, "gui.settings.unequipLore")
                                    : plugin.lang().text(player, "gui.settings.equipLore"))));
        }

        // Row 2: Dynamic sharing mode button (left)
        int slotSharing = SLOT_SHARING;

        if (data.isShared()) {
            if (data.isShareHost()) {
                // Currently hosting
                List<String> lore = new ArrayList<>();
                lore.add(plugin.lang().text(player, "gui.settings.hosting"));
                lore.add(plugin.lang().text(player, "gui.settings.password", "password",
                        data.sharePassword() == null || data.sharePassword().isEmpty()
                                ? plugin.lang().text(player, "gui.settings.noPassword")
                                : data.sharePassword()));
                lore.add("&7");
                lore.add(plugin.lang().text(player, "gui.settings.stopHosting"));
                lore.add(plugin.lang().text(player, "gui.settings.setPassword"));
                inv.setItem(slotSharing,
                        createButton(plugin.lang().text(player, "gui.settings.host"), Material.ENDER_EYE, lore));

            } else {
                // Currently joined
                List<String> lore = new ArrayList<>();
                lore.add(plugin.lang().text(player, "gui.settings.joinedLore"));
                lore.add("&7");
                lore.add(plugin.lang().text(player, "gui.settings.leave"));
                lore.add(plugin.lang().text(player, "gui.settings.changeHost"));
                inv.setItem(slotSharing,
                        createButton(plugin.lang().text(player, "gui.settings.joined"), Material.ENDER_PEARL, lore));
            }
        } else {
            // Private mode
            List<String> lore = new ArrayList<>();
            lore.add(plugin.lang().text(player, "gui.settings.privateLore"));
            lore.add("&7");
            lore.add(plugin.lang().text(player, "gui.settings.startHosting"));
            lore.add(plugin.lang().text(player, "gui.settings.join"));
            inv.setItem(slotSharing,
                    createButton(plugin.lang().text(player, "gui.settings.private"), Material.BARRIER, lore));
        }

        // Row 2: Color settings (center)
        slotSharing = SLOT_COLORS;
        int[] colors = BackpackColorTints.getColors(backpackItem);
        List<String> colorLore = new ArrayList<>();
        colorLore.add(plugin.lang().text(player, "gui.settings.colorsDescription"));
        colorLore.add("&7");
        colorLore.add(plugin.lang().text(player, "gui.settings.currentColors"));
        for (int i = 0; i < 5; i++) {
            colorLore.add(plugin.lang().text(player, "gui.colors.currentGroup", "group", groupLabel(player, i), "color",
                    String.format("%06X", colors[i])));
        }
        colorLore.add("&7");
        colorLore.add(plugin.lang().text(player, "gui.settings.openColors"));
        inv.setItem(slotSharing, createColorPreviewItem(player, backpackItem, colorLore));

        // Row 2: Name button (right)
        slotSharing = SLOT_NAME;

        // Backpack name button
        List<String> nameLore = new ArrayList<>();
        String currentName = data.backpackName();
        if (currentName.isEmpty()) {
            nameLore.add(plugin.lang().text(player, "gui.settings.noName"));
        } else {
            nameLore.add(plugin.lang().text(player, "gui.settings.currentName", "name", currentName));
        }
        nameLore.add("&7");
        nameLore.add(plugin.lang().text(player, "gui.settings.setName"));
        inv.setItem(slotSharing,
                createButton(plugin.lang().text(player, "gui.settings.name"), Material.NAME_TAG, nameLore));

        // Row 3: Back button (bottom center)
        slotSharing = SLOT_BACK;
        List<String> backLore = new ArrayList<>();
        backLore.add(plugin.lang().text(player, "gui.settings.backLore"));
        inv.setItem(slotSharing,
                createButton(plugin.lang().text(player, "gui.settings.back"), Material.ARROW, backLore));

        // Decorative frame for cleaner visual grouping
        ItemStack frame = createButton("&8", Material.GRAY_STAINED_GLASS_PANE,
                List.of(plugin.lang().text(player, "gui.settings.title")));
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, frame);
            }
        }

        player.openInventory(inv);
    }

    /**
     * Handle clicks in the settings menu.
     */
    public void handleClick(Player player, BackpackMenuHolder holder, int slot, ClickType click) {
        BackpackData data = holder.data();

        if (slot == SLOT_RESOURCE_PACKS) {
            if (plugin.cfg().resourcePackEnabled()) {
                plugin.resourcePacks().openMenu(player, holder);
            }
            return;
        }

        // Equip button (top right)
        if (slot == SLOT_EQUIP) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()
                        && player.getOpenInventory().getTopInventory()
                                .getHolder() instanceof SettingsMenuHolder settings
                        && settings.backpackMenuHolder() == holder) {
                    toggleEquipped(player, holder);
                }
            });
            return;
        }

        // Row 1: Dynamic mode button
        if (slot == SLOT_SHARING) {
            if (data.isShared()) {
                if (data.isShareHost()) {
                    if (click.isRightClick()) {
                        sharingHost.openPasswordDialog(player, holder);
                    } else {
                        sharingHost.stopHosting(player, holder);
                        openSettingsMenu(player, holder);
                    }
                } else {
                    if (click.isRightClick()) {
                        sharingJoiner.openJoinDialog(player, holder);
                    } else {
                        sharingJoiner.leaveJoinedBackpack(player, holder);
                        player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.left")));
                        openSettingsMenu(player, holder);
                    }
                }
            } else {
                if (click.isRightClick()) {
                    sharingJoiner.openJoinDialog(player, holder);
                } else {
                    sharingHost.hostBackpack(player, holder);
                    openSettingsMenu(player, holder);
                }
            }
            return;
        }

        // Row 2: Single colors button
        if (slot == SLOT_COLORS) {
            openColorPickerMenu(player, holder);
            return;
        }

        // Row 3: Name button
        if (slot == SLOT_NAME) {
            openNameDialog(player, holder);
            return;
        }

        // Back button
        if (slot == SLOT_BACK) {
            plugin.getBackpackMenuRenderer().openMenu(player, holder.backpackId(), holder.type().id(), holder.page());
            return;
        }
    }

    /**
     * Handle clicks in hopper-based color picker.
     */
    public void handleColorPickerClick(Player player, BackpackMenuHolder holder, int slot, ClickType click) {
        ItemStack backpackItem = resolveBackpackItemForSettings(player, holder);
        if (backpackItem == null) {
            player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.missingEditItem")));
            return;
        }

        // Slots 0-4 map directly to editable custom_model_data color indices 0-4.
        // Slot 5 (tier/index 5) is intentionally not exposed.
        if (slot >= 0 && slot <= 4) {
            if (click.isRightClick()) {
                BackpackColorTints.clearColorTint(backpackItem, slot);
                persistVisualChanges(holder, backpackItem);
                player.sendMessage(
                        Text.c(plugin.lang().text(player, "gui.colors.removed", "group", groupLabel(player, slot))));
                openColorPickerMenu(player, holder);
            } else {
                openCustomRgbDialog(player, holder, backpackItem, slot);
            }
        }
    }

    private void openColorPickerMenu(Player player, BackpackMenuHolder holder) {
        ColorPickerHolder colorHolder = new ColorPickerHolder(holder.backpackId(), holder);
        Inventory picker = plugin.getServer().createInventory(colorHolder, InventoryType.HOPPER,
                Text.c(plugin.lang().text(player, "gui.settings.colorsTitle")));

        ItemStack backpackItem = resolveBackpackItemForSettings(player, holder);
        if (backpackItem == null) {
            player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.missingEditItem")));
            return;
        }

        int[] colors = BackpackColorTints.getColors(backpackItem);
        for (int index = 0; index < 5; index++) {
            picker.setItem(index, createButton(plugin.lang().text(player, "gui.colors.groupName",
                    "group", groupLabel(player, index)), Material.NAME_TAG,
                    plugin.lang().lines(player, "gui.colors.pickerLore", "index", index,
                            "color", String.format("%06X", colors[index]))));
        }

        player.openInventory(picker);
    }

    private void openCustomRgbDialog(Player player, BackpackMenuHolder holder, ItemStack backpackItem, int colorIndex) {
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return java.util.Collections.emptyList();
                    }

                    String input = stateSnapshot.getText();
                    Integer rgb = parseRgbInput(input);
                    if (rgb == null) {
                        player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.invalidColor")));
                        return java.util.Collections.emptyList();
                    }

                    BackpackColorTints.setColorTint(backpackItem, colorIndex, rgb);
                    persistVisualChanges(holder, backpackItem);
                    player.sendMessage(
                            Text.c(plugin.lang().text(player, "gui.colors.updated", "group",
                                    groupLabel(player, colorIndex), "color", String.format("%06X", rgb))));

                    return java.util.Arrays.asList(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> plugin.getServer().getScheduler().runTask(plugin,
                                    () -> openColorPickerMenu(player, holder))));
                })
                .text("#FFAA33")
                .title(plugin.lang().text(player, "gui.colors.dialogTitle", "group", groupLabel(player, colorIndex)))
                .plugin(plugin)
                .open(player);
    }

    private Integer parseRgbInput(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        // Decimal integer path
        if (normalized.matches("^[0-9]+$")) {
            try {
                int decimal = Integer.parseInt(normalized);
                if (decimal < 0 || decimal > 0xFFFFFF) {
                    return null;
                }
                return decimal;
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        // Hex path (#RRGGBB, 0xRRGGBB, or RRGGBB)
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }

        if (!normalized.matches("^[0-9A-Fa-f]{6}$")) {
            return null;
        }

        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Open a dialog to set the backpack name.
     */
    private void openNameDialog(Player player, BackpackMenuHolder holder) {
        String currentName = holder.data().backpackName();

        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return java.util.Collections.emptyList();
                    }

                    String name = stateSnapshot.getText();
                    if (name == null) {
                        name = "";
                    }

                    // Trim and validate
                    name = name.trim();
                    if (name.length() > 32) {
                        name = name.substring(0, 32);
                    }

                    holder.data().backpackName(name);
                    plugin.repo().saveBackpack(holder.data());
                    plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), holder.data());

                    if (name.isEmpty()) {
                        player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.nameReset")));
                    } else {
                        player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.nameSet", "name", name)));
                    }

                    return java.util.Arrays.asList(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    openSettingsMenu(player, holder);
                                });
                            }));
                })
                .text(currentName.isEmpty() ? plugin.lang().text(player, "gui.settings.namePrompt") : currentName)
                .title(plugin.lang().text(player, "gui.settings.nameTitle"))
                .plugin(plugin)
                .open(player);
    }

    /**
     * Helper to create a button with name and lore.
     */
    private ItemStack createButton(String name, Material material, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            meta.lore(Text.lore(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createColorPreviewItem(Player player, ItemStack backpackItem, List<String> lore) {
        ItemStack preview = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta == null) {
            return createButton(plugin.lang().text(player, "gui.settings.colors"), Material.PLAYER_HEAD, lore);
        }

        previewMeta.displayName(Text.c(plugin.lang().text(player, "gui.settings.colors")));
        previewMeta.lore(Text.lore(lore));

        if (backpackItem != null) {
            ItemMeta sourceMeta = backpackItem.getItemMeta();
            if (sourceMeta != null) {
                int customModelData = CustomModelDataUtil.getCustomModelData(sourceMeta);
                if (customModelData > 0) {
                    CustomModelDataUtil.setCustomModelData(previewMeta, customModelData);
                }

                CustomModelDataUtil.setCustomModelDataStrings(previewMeta,
                        CustomModelDataUtil.getCustomModelDataStrings(sourceMeta));
                CustomModelDataUtil.setCustomModelDataFlags(previewMeta,
                        CustomModelDataUtil.getCustomModelDataFlags(sourceMeta));
                CustomModelDataUtil.setCustomModelDataColors(previewMeta,
                        CustomModelDataUtil.getCustomModelDataColors(sourceMeta));
            }
        }

        preview.setItemMeta(previewMeta);
        return preview;
    }

    private String groupLabel(Player player, int groupIndex) {
        if (groupIndex < 0 || groupIndex >= COLOR_GROUP_NAMES.length) {
            return plugin.lang().text(player, "gui.colors.group", "index", groupIndex + 1);
        }
        return plugin.lang().get(player, "gui.colors.groups." + groupIndex, COLOR_GROUP_NAMES[groupIndex]);
    }

    private boolean matchesBackpack(ItemStack item, UUID backpackId) {
        return backpackId != null && backpackItems.isBackpack(item)
                && backpackId.toString().equals(item.getItemMeta().getPersistentDataContainer()
                        .get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING));
    }

    private void toggleEquipped(Player player, BackpackMenuHolder holder) {
        if (holder.isPlacedContext()) {
            player.sendMessage(Text.c(plugin.lang().text(player, "gui.settings.pickUpFirst")));
            return;
        }

        if (plugin.modelManager().backSlot().toggle(player, holder.backpackId())) {
            plugin.modelManager().scanPlayerForModels(player);
            openSettingsMenu(player, holder);
        }
    }

    /**
     * Find the backpack ItemStack in the player's inventory by looking for the
     * backpack ID in the item's PersistentDataContainer.
     */
    private ItemStack findBackpackInInventory(Player player, UUID backpackId) {
        if (backpackId == null) {
            return null;
        }

        // Search player inventory for the backpack item
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getItemMeta() == null) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            String itemBackpackId = pdc.get(plugin.keys().BACKPACK_ID,
                    PersistentDataType.STRING);
            if (itemBackpackId != null && backpackId.toString().equals(itemBackpackId)) {
                return item;
            }
        }

        // If not found in inventory scan, explicitly check main hand and offhand by
        // exact ID
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getItemMeta() != null) {
            String id = mainHand.getItemMeta().getPersistentDataContainer().get(plugin.keys().BACKPACK_ID,
                    PersistentDataType.STRING);
            if (id != null && backpackId.toString().equals(id)) {
                return mainHand;
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getItemMeta() != null) {
            String id = offHand.getItemMeta().getPersistentDataContainer().get(plugin.keys().BACKPACK_ID,
                    PersistentDataType.STRING);
            if (id != null && backpackId.toString().equals(id)) {
                return offHand;
            }
        }

        return null;
    }

    private ItemStack resolveBackpackItemForSettings(Player player, BackpackMenuHolder holder) {
        if (holder == null) {
            return null;
        }

        if (holder.isPlacedContext()) {
            Location placedLocation = holder.placedLocation();
            if (placedLocation == null) {
                return null;
            }

            PlacedBackpack placed = plugin.placedBackpacks().getAt(placedLocation);
            if (placed == null) {
                return null;
            }

            ItemStack item = backpackItems.createExisting(placed.backpackId(), placed.backpackType());
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }

            CustomModelDataUtil.setCustomModelDataStrings(meta,
                    placed.modelDataStrings());
            if (!placed.modelDataColors().isEmpty()) {
                List<Color> colors = new ArrayList<>(placed.modelDataColors().size());
                for (Integer rgb : placed.modelDataColors()) {
                    int value = rgb == null ? 0xFFFFFF : (rgb & 0xFFFFFF);
                    colors.add(Color.fromRGB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF));
                }
                CustomModelDataUtil.setCustomModelDataColors(meta, colors);
            }

            item.setItemMeta(meta);
            return item;
        }

        ItemStack equipped = plugin.modelManager().equippedBackpack(player);
        return matchesBackpack(equipped, holder.backpackId()) ? equipped
                : findBackpackInInventory(player, holder.backpackId());
    }

    private void persistVisualChanges(BackpackMenuHolder holder, ItemStack backpackItem) {
        if (holder == null || backpackItem == null) {
            return;
        }

        if (!holder.isPlacedContext()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (plugin.modelManager().backSlot().isEquipped(player, holder.backpackId())) {
                    plugin.modelManager().backSlot().update(player, backpackItem);
                    player.saveData();
                    plugin.modelManager().scanPlayerForModels(player);
                }
            }
            return;
        }

        Location placedLocation = holder.placedLocation();
        if (placedLocation != null) {
            plugin.placedBackpacks().updatePlacedVisuals(placedLocation, backpackItem);
        }
    }
}
