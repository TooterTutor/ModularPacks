package io.github.tootertutor.ModularPacks.compat.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.compat.CuriosCompat;
import io.github.tootertutor.ModularPacks.compat.CuriosItemTagger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class CuriosBackpackOpenListener implements Listener {

    private static final String CURIOS_BACK_SLOT = "back";
    private static final int BACKPACKS_PER_PAGE = 3;
    private static final int PREV_ARROW_SLOT = 0;
    private static final int FIRST_BACKPACK_SLOT = 1;
    private static final int NEXT_ARROW_SLOT = 4;

    private final ModularPacksPlugin plugin;
    private final CuriosCompat curiosCompat;

    private final Set<UUID> pendingTagRestoration = Collections.synchronizedSet(new HashSet<>());

    public CuriosBackpackOpenListener(ModularPacksPlugin plugin, CuriosCompat curiosCompat) {
        this.plugin = plugin;
        this.curiosCompat = curiosCompat;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractLowest(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isBackpackItem(item)) {
            return;
        }

        CuriosItemTagger.removeBackpackTags(item);
        player.getInventory().setItemInMainHand(item);
        pendingTagRestoration.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractMonitor(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!pendingTagRestoration.remove(player.getUniqueId())) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isBackpackItem(item)) {
            return;
        }

        if (getBackpackType(item) != null) {
            CuriosItemTagger.syncBackpackTags(plugin, item);
            player.getInventory().setItemInMainHand(item);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!player.isSneaking()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        openEquippedBackpack(player);
        event.setCancelled(true);
    }

    @EventHandler
    public void onBackpackMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackSelectionHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        if (clicked.getType() == Material.ARROW) {
            handlePaginationClick(event, player);
        } else {
            handleBackpackClick(player, clicked);
        }
    }

    private void openEquippedBackpack(Player player) {
        List<ItemStack> backpacksWithId = getValidEquippedBackpacks(player);
        if (backpacksWithId.isEmpty()) {
            player.sendMessage(Component.text("No valid backpack equipped", NamedTextColor.GOLD));
            return;
        }

        if (backpacksWithId.size() == 1) {
            openBackpack(player, backpacksWithId.get(0));
        } else {
            showBackpackMenu(player, backpacksWithId, 0);
        }
    }

    private List<ItemStack> getValidEquippedBackpacks(Player player) {
        List<ItemStack> equippedItems = curiosCompat.getEquippedItems(player, CURIOS_BACK_SLOT);
        if (equippedItems == null || equippedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> backpacksWithId = new ArrayList<>();
        for (ItemStack item : equippedItems) {
            if (item != null && !item.getType().isAir() && hasValidBackpackData(item)) {
                backpacksWithId.add(item);
            }
        }

        return backpacksWithId;
    }

    private void openBackpack(Player player, ItemStack backpack) {
        String backpackId = getBackpackId(backpack);
        String backpackType = getBackpackType(backpack);

        if (backpackId == null || backpackType == null) {
            return;
        }

        try {
            UUID backpackUUID = UUID.fromString(backpackId);
            plugin.getBackpackMenuRenderer().openMenu(player, backpackUUID, backpackType);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Error opening backpack: Invalid backpack ID", NamedTextColor.RED));
        } catch (Exception e) {
            player.sendMessage(Component.text("Error opening backpack: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void showBackpackMenu(Player player, List<ItemStack> backpacks, int page) {
        Component menuTitle = Component.text("Select Backpack");
        BackpackSelectionHolder holder = new BackpackSelectionHolder(backpacks, page);
        Inventory menu = Bukkit.createInventory(holder, InventoryType.HOPPER, menuTitle);
        holder.setInventory(menu);

        populateBackpackSlots(menu, backpacks, page);
        addPaginationArrows(menu, backpacks.size(), page);

        player.openInventory(menu);
    }

    private void populateBackpackSlots(Inventory menu, List<ItemStack> backpacks, int page) {
        int startIndex = page * BACKPACKS_PER_PAGE;
        int endIndex = Math.min(startIndex + BACKPACKS_PER_PAGE, backpacks.size());

        int slotIndex = FIRST_BACKPACK_SLOT;
        for (int i = startIndex; i < endIndex; i++) {
            menu.setItem(slotIndex++, backpacks.get(i));
        }
    }

    private void addPaginationArrows(Inventory menu, int totalBackpacks, int currentPage) {
        int maxPage = (totalBackpacks + BACKPACKS_PER_PAGE - 1) / BACKPACKS_PER_PAGE - 1;

        if (currentPage > 0) {
            menu.setItem(PREV_ARROW_SLOT, createArrow("Previous"));
        }

        if (currentPage < maxPage) {
            menu.setItem(NEXT_ARROW_SLOT, createArrow("Next"));
        }
    }

    private ItemStack createArrow(String label) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label)
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to navigate").color(NamedTextColor.GRAY));
            meta.lore(lore);
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    private void handlePaginationClick(InventoryClickEvent event, Player player) {
        BackpackSelectionHolder holder = (BackpackSelectionHolder) event.getInventory().getHolder();
        List<ItemStack> backpacks = holder.getBackpacks();
        int currentPage = holder.getCurrentPage();
        int maxPage = (backpacks.size() + BACKPACKS_PER_PAGE - 1) / BACKPACKS_PER_PAGE - 1;

        int rawSlot = event.getRawSlot();
        if (rawSlot == PREV_ARROW_SLOT && currentPage > 0) {
            showBackpackMenu(player, backpacks, currentPage - 1);
        } else if (rawSlot == NEXT_ARROW_SLOT && currentPage < maxPage) {
            showBackpackMenu(player, backpacks, currentPage + 1);
        }
    }

    private void handleBackpackClick(Player player, ItemStack backpackItem) {
        openBackpack(player, backpackItem);
    }

    private boolean hasValidBackpackData(ItemStack item) {
        return getBackpackId(item) != null && getBackpackType(item) != null;
    }

    private boolean isBackpackItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }

        String backpackId = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING);

        return backpackId != null && !backpackId.isEmpty();
    }

    private String getBackpackId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().BACKPACK_ID, PersistentDataType.STRING);
    }

    private String getBackpackType(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.keys().BACKPACK_TYPE, PersistentDataType.STRING);
    }
}
