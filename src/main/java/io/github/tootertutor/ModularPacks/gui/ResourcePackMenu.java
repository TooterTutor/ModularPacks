package io.github.tootertutor.ModularPacks.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.resource.ResourcePackDefinition;
import io.github.tootertutor.ModularPacks.resource.ResourcePackService;
import io.github.tootertutor.ModularPacks.update.PackCheckerService.AvailablePack;
import io.github.tootertutor.ModularPacks.util.Text;

/** GUI for composing and applying a stack of up to four optional resource packs. */
public final class ResourcePackMenu {

    private static final int GUI_SIZE = 18;
    private static final int MAX_PACKS = 4;
    private static final int PACK_GAP = 1;

    private static final int NAV_BACK = 0;
    private static final int NAV_DISABLE_ALL = 3;
    private static final int NAV_APPLY = 4;
    private static final int NAV_DEFAULTS = 5;

    private final ModularPacksPlugin plugin;
    private final ResourcePackService service;

    public ResourcePackMenu(ModularPacksPlugin plugin, ResourcePackService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player, BackpackMenuHolder source) {
        LinkedHashSet<String> selected = new LinkedHashSet<>(service.selectionFor(player));
        ResourcePackMenuHolder holder = new ResourcePackMenuHolder(source, selected);
        Inventory inventory = plugin.getServer().createInventory(holder, GUI_SIZE,
                Text.c("&8ModularPacks Resource Packs"));
        render(player, holder, inventory);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, ResourcePackMenuHolder holder, int slot) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        List<ResourcePackDefinition> definitions = visibleDefinitions();
        List<Integer> packSlots = packSlots(definitions.size());
        int definitionIndex = packSlots.indexOf(slot);

        if (definitionIndex >= 0) {
            String id = definitions.get(definitionIndex).id();
            if (!holder.selected().remove(id)) {
                holder.selected().add(id);
            }
            render(player, holder, inventory);
            return;
        }

        NavRow nav = navRow(inventory.getSize());
        if (slot == nav.back()) {
            BackpackMenuHolder source = holder.source();
            plugin.getBackpackMenuRenderer().openMenu(player, source.backpackId(), source.type().id(), source.page());
            return;
        }
        if (slot == nav.disableAll()) {
            holder.selected().clear();
            render(player, holder, inventory);
            return;
        }
        if (slot == nav.apply()) {
            service.saveAndApply(player, holder.selected());
            player.closeInventory();
            return;
        }
        if (slot == nav.defaults()) {
            holder.selected().clear();
            holder.selected().addAll(plugin.cfg().defaultResourcePacks());
            render(player, holder, inventory);
        }
    }

    private void render(Player player, ResourcePackMenuHolder holder, Inventory inventory) {
        inventory.clear();
        int navStart = SlotLayout.bottomRowStart(inventory.getSize());

        ItemStack filler = item(plugin.cfg().navBorderFiller(), "&8", List.of());
        for (int slot = navStart; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        List<ResourcePackDefinition> definitions = visibleDefinitions();
        List<Integer> slots = packSlots(definitions.size());
        for (int index = 0; index < definitions.size(); index++) {
            ResourcePackDefinition definition = definitions.get(index);
            boolean selected = holder.selected().contains(definition.id());
            AvailablePack available = service.availablePack(definition.id());
            String loadedHash = service.loadedHash(player, definition.id());
            List<String> lore = new ArrayList<>();
            lore.add(selected ? "&aEnabled in selection" : "&7Disabled in selection");
            lore.add("&7Stack priority: &f" + definition.priority());
            if (available == null) {
                lore.add("&cNot downloaded yet");
            } else {
                lore.add("&7Available: &f" + available.sha1().substring(0, 8));
                if (loadedHash != null && loadedHash.equals(available.sha1())) {
                    lore.add("&aCurrently loaded");
                } else if (loadedHash != null) {
                    lore.add("&eUpdate ready");
                }
            }
            lore.add("");
            lore.add("&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Toggle this layer");
            String name = (selected ? "&a✔ " : "&7") + definition.displayName();
            inventory.setItem(slots.get(index), item(definition.icon(), name, lore));
        }

        NavRow nav = navRow(inventory.getSize());
        inventory.setItem(nav.back(), item(plugin.cfg().navPageButtons(), "&eBack to Backpack", List.of()));
        inventory.setItem(nav.disableAll(), item(Material.BARRIER, "&cDisable All",
                List.of("&7Clear every ModularPacks layer", "&7from the staged selection.")));
        inventory.setItem(nav.apply(), item(Material.LIME_CONCRETE, "&aApply Selected Packs",
                List.of("&7Save and apply &f" + holder.selected().size() + "&7 selected layer(s).",
                        "&7All changes are sent together.")));
        inventory.setItem(nav.defaults(), item(Material.RECOVERY_COMPASS, "&eSelect Default",
                List.of("&7Restore the server's default", "&7resource-pack selection.")));
    }

    private List<ResourcePackDefinition> visibleDefinitions() {
        List<ResourcePackDefinition> definitions = service.definitions();
        return definitions.size() <= MAX_PACKS ? definitions : definitions.subList(0, MAX_PACKS);
    }

    private static List<Integer> packSlots(int packCount) {
        return SlotLayout.centeredRowSlots(0, Math.min(MAX_PACKS, packCount), PACK_GAP);
    }

    private static NavRow navRow(int inventorySize) {
        return new NavRow(
                SlotLayout.navRowSlot(inventorySize, NAV_BACK),
                SlotLayout.navRowSlot(inventorySize, NAV_DISABLE_ALL),
                SlotLayout.navRowSlot(inventorySize, NAV_APPLY),
                SlotLayout.navRowSlot(inventorySize, NAV_DEFAULTS));
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(name));
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    private record NavRow(int back, int disableAll, int apply, int defaults) {
    }
}
