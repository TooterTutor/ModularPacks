package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.util.Text;
import net.kyori.adventure.text.Component;

/**
 * Five-slot, read-only carousel of projectile identities in backpack storage.
 */
public final class QuiverSelectionMenu implements Listener {

    private static final int PREVIOUS_PAGE_SLOT = 0;
    private static final int PREVIOUS_PROJECTILE_SLOT = 1;
    private static final int SELECTED_PROJECTILE_SLOT = 2;
    private static final int NEXT_PROJECTILE_SLOT = 3;
    private static final int NEXT_PAGE_SLOT = 4;
    private static final int PAGE_STEP = 3;

    private final ModularPacksPlugin plugin;
    private final QuiverAmmoService ammoService;
    private final QuiverSourceResolver resolver;

    public QuiverSelectionMenu(ModularPacksPlugin plugin, QuiverAmmoService ammoService,
            QuiverSourceResolver resolver) {
        this.plugin = plugin;
        this.ammoService = ammoService;
        this.resolver = resolver;
    }

    public void open(Player player, UUID backpackId, String backpackType, UUID moduleId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
            if (!data.installedModules().containsValue(moduleId)) {
                return;
            }
            QuiverSelectionMenuHolder holder = new QuiverSelectionMenuHolder(backpackId, backpackType, moduleId);
            Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER,
                    Text.c(plugin.lang().text(player, "gui.quiver.title")));
            holder.inventory(inventory);
            render(player, holder);
            player.openInventory(inventory);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof QuiverSelectionMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= InventoryType.HOPPER.getDefaultSize()) {
            return;
        }

        QuiverSelection selection;
        if (rawSlot == SELECTED_PROJECTILE_SLOT && event.isRightClick()) {
            BackpackData data = plugin.repo().loadOrCreate(holder.backpackId(), holder.backpackType());
            QuiverSelection current = resolver.readSelection(data, holder.moduleId());
            if (current.mode() == QuiverSelectionMode.EXACT) {
                selection = QuiverSelection.auto();
            } else {
                ItemStack selected = holder.choice(SELECTED_PROJECTILE_SLOT);
                if (selected == null) {
                    return;
                }
                selection = QuiverSelection.exact(selected);
            }
        } else if (rawSlot != SELECTED_PROJECTILE_SLOT) {
            ItemStack selected = holder.choice(rawSlot);
            if (selected == null) {
                return;
            }
            selection = QuiverSelection.exact(selected);
        } else {
            return;
        }

        if (resolver.saveSelection(holder.backpackId(), holder.backpackType(), holder.moduleId(), selection)) {
            render(player, holder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QuiverSelectionMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof QuiverSelectionMenuHolder holder) {
            // Display contents are projections only. Closing releases the session
            // lock and deliberately serializes no inventory contents.
            plugin.sessions().onRelatedInventoryClose(player, holder.backpackId());
        }
    }

    private void render(Player player, QuiverSelectionMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearChoices();

        BackpackData data = plugin.repo().loadOrCreate(holder.backpackId(), holder.backpackType());
        var type = plugin.cfg().findType(data.backpackType());
        if (type == null) {
            return;
        }
        BackpackStorage storage = plugin.backpackStorage().load(data, type.rows() * 9);
        List<ProjectileOption> options = new ArrayList<>();
        for (QuiverAmmoService.AmmoOption option : ammoService.availableOptions(storage)) {
            options.add(new ProjectileOption(option.prototype(), option.count()));
        }
        QuiverSelection selection = resolver.readSelection(data, holder.moduleId());

        ItemStack selectedPrototype = selection.mode() == QuiverSelectionMode.EXACT
                ? selection.selectedPrototype()
                : ammoService.select(QuiverSelection.auto(), storage);
        int selectedIndex = indexOf(options, selectedPrototype);
        if (selection.mode() == QuiverSelectionMode.EXACT && selectedPrototype != null && selectedIndex < 0) {
            options.add(new ProjectileOption(selectedPrototype, 0));
            selectedIndex = options.size() - 1;
        }

        if (selectedIndex < 0) {
            inventory.setItem(SELECTED_PROJECTILE_SLOT,
                    control(Material.PAPER, plugin.lang().text(player, "gui.quiver.empty"),
                            List.of(plugin.lang().text(player, "gui.quiver.emptyLore"))));
            return;
        }

        ProjectileOption selected = options.get(selectedIndex);
        inventory.setItem(SELECTED_PROJECTILE_SLOT, selectedDisplay(player, selected, selection.mode()));
        holder.choice(SELECTED_PROJECTILE_SLOT, selected.prototype());
        setNavigationChoice(player, inventory, holder, options, selectedIndex - 1,
                PREVIOUS_PROJECTILE_SLOT, plugin.lang().text(player, "gui.quiver.previousProjectile"));
        setNavigationChoice(player, inventory, holder, options, selectedIndex + 1,
                NEXT_PROJECTILE_SLOT, plugin.lang().text(player, "gui.quiver.nextProjectile"));
        setPageChoice(player, inventory, holder, options, selectedIndex - PAGE_STEP,
                PREVIOUS_PAGE_SLOT, plugin.lang().text(player, "gui.quiver.previous"));
        setPageChoice(player, inventory, holder, options, selectedIndex + PAGE_STEP,
                NEXT_PAGE_SLOT, plugin.lang().text(player, "gui.quiver.next"));
    }

    private void setNavigationChoice(Player player, Inventory inventory, QuiverSelectionMenuHolder holder,
            List<ProjectileOption> options, int index, int slot, String label) {
        if (index < 0 || index >= options.size()) {
            return;
        }
        ProjectileOption option = options.get(index);
        inventory.setItem(slot, optionDisplay(player, option, label));
        holder.choice(slot, option.prototype());
    }

    private void setPageChoice(Player player, Inventory inventory, QuiverSelectionMenuHolder holder,
            List<ProjectileOption> options, int targetIndex, int slot, String label) {
        if (targetIndex < 0 || targetIndex >= options.size()) {
            return;
        }
        ProjectileOption target = options.get(targetIndex);
        inventory.setItem(slot, control(Material.ARROW, label,
                List.of(plugin.lang().text(player, "gui.quiver.jump"))));
        holder.choice(slot, target.prototype());
    }

    private ItemStack optionDisplay(Player player, ProjectileOption option, String label) {
        ItemStack display = option.prototype();
        display.setAmount(Math.toIntExact(Math.min(option.count(), display.getMaxStackSize())));
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.c(label));
        lore.add(Text.c(plugin.lang().text(player, "gui.quiver.stored", "count", option.count())));
        lore.add(Text.c(plugin.lang().text(player, "gui.quiver.select")));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack selectedDisplay(Player player, ProjectileOption option, QuiverSelectionMode mode) {
        ItemStack display = option.prototype();
        display.setAmount(Math.toIntExact(Math.max(1, Math.min(option.count(), display.getMaxStackSize()))));
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.c(mode == QuiverSelectionMode.AUTO ? plugin.lang().text(player, "gui.quiver.auto")
                : plugin.lang().text(player, "gui.quiver.exact")));
        lore.add(Text.c(option.count() > 0
                ? plugin.lang().text(player, "gui.quiver.available", "count", option.count())
                : plugin.lang().text(player, "gui.quiver.unavailable")));
        lore.add(Text.c(mode == QuiverSelectionMode.AUTO
                ? plugin.lang().text(player, "gui.quiver.chooseExact")
                : plugin.lang().text(player, "gui.quiver.chooseAuto")));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack control(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(name));
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    private int indexOf(List<ProjectileOption> options, ItemStack prototype) {
        if (prototype == null) {
            return -1;
        }
        for (int index = 0; index < options.size(); index++) {
            if (plugin.backpackStorage().identity().sameIdentity(options.get(index).prototype(), prototype)) {
                return index;
            }
        }
        return -1;
    }

    private record ProjectileOption(ItemStack storedPrototype, long count) {
        private ProjectileOption {
            storedPrototype = storedPrototype.clone();
            storedPrototype.setAmount(1);
        }

        public ItemStack prototype() {
            return storedPrototype.clone();
        }
    }
}
