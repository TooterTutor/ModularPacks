package io.github.tootertutor.ModularPacks.gui;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.ModularPacksAPI;
import io.github.tootertutor.ModularPacks.api.modules.ModuleRegistry;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.modules.AnvilModule;
import io.github.tootertutor.ModularPacks.modules.CraftingModule;
import io.github.tootertutor.ModularPacks.modules.FurnaceModule;
import io.github.tootertutor.ModularPacks.modules.SmithingModule;
import io.github.tootertutor.ModularPacks.modules.StonecutterModule;
import net.kyori.adventure.text.Component;

public final class ScreenRouter {

	private final ModularPacksPlugin plugin;

	private final SmithingModule smithingModule;
	private final StonecutterModule stonecutterModule;
	private final CraftingModule craftingModule;
	private final AnvilModule anvilModule;
	private final FurnaceModule furnaceModule;

	public ScreenRouter(ModularPacksPlugin plugin) {
		this.plugin = plugin;
		this.smithingModule = new SmithingModule();
		this.stonecutterModule = new StonecutterModule();
		this.craftingModule = new CraftingModule();
		this.anvilModule = new AnvilModule();
		this.furnaceModule = new FurnaceModule();

		// Ensure the central registry can see these built-in modules (used for session
		// tracking and API access). Duplicate registrations throw, so guard
		// best-effort.
		ModuleRegistry registry = ModularPacksAPI.getInstance().getModuleRegistry();
		try {
			registry.registerModule(this.smithingModule);
			registry.registerModule(this.stonecutterModule);
			registry.registerModule(this.craftingModule);
			registry.registerModule(this.anvilModule);
			registry.registerModule(this.furnaceModule);
		} catch (IllegalArgumentException ex) {
			plugin.getLogger().warning("Module already registered: " + ex.getMessage());
		}
	}

	public FurnaceModule getFurnaceModule() {
		return furnaceModule;
	}

	public SmithingModule getSmithingModule() {
		return smithingModule;
	}

	public StonecutterModule getStonecutterModule() {
		return stonecutterModule;
	}

	public CraftingModule getCraftingModule() {
		return craftingModule;
	}

	public AnvilModule getAnvilModule() {
		return anvilModule;
	}

	private void openNextTick(Runnable task) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			try {
				task.run();
			} catch (Exception ex) {
				plugin.getLogger().severe("Module open failed: " + ex.getMessage());
				ex.printStackTrace();
			}
		});
	}

	/**
	 * Open a module screen backed by moduleStates (persistent).
	 */
	public void open(Player player, UUID backpackId, String backpackType, UUID moduleId, ScreenType screenType) {
		if (screenType == ScreenType.NONE)
			return;

		// plugin.getLogger().info("Opening module screen " + screenType + " for " +
		// player.getName() + " backpack="
		// + backpackId + " module=" + moduleId);

		if (screenType == ScreenType.ANVIL) {
			openNextTick(() -> anvilModule.open(plugin, player, backpackId, backpackType, moduleId));
			return;
		}

		if (screenType == ScreenType.CRAFTING) {
			openNextTick(() -> craftingModule.open(plugin, player, backpackId, backpackType, moduleId));
			return;
		}

		if (screenType == ScreenType.SMITHING) {
			openNextTick(() -> smithingModule.open(plugin, player, backpackId, backpackType, moduleId));
			return;
		}

		if (screenType == ScreenType.STONECUTTER) {
			openNextTick(() -> stonecutterModule.open(plugin, player, backpackId, backpackType, moduleId));
			return;
		}

		if (screenType == ScreenType.SMELTING || screenType == ScreenType.BLASTING
				|| screenType == ScreenType.SMOKING) {
			openNextTick(() -> furnaceModule.open(plugin, player, backpackId, backpackType, moduleId, screenType));
			return;
		}

		// Holder so listeners can identify and persist this module screen
		ModuleScreenHolder holder = new ModuleScreenHolder(backpackId, backpackType, moduleId, screenType);

		Inventory inv = switch (screenType) {
			case CRAFTING -> Bukkit.createInventory(holder, InventoryType.WORKBENCH, Component.text("Crafting Module"));
			case SMITHING -> Bukkit.createInventory(holder, InventoryType.SMITHING, Component.text("Smithing Module"));
			case SMELTING -> Bukkit.createInventory(holder, InventoryType.FURNACE, Component.text("Smelting Module"));
			case BLASTING ->
				Bukkit.createInventory(holder, InventoryType.BLAST_FURNACE, Component.text("Blasting Module"));
			case SMOKING -> Bukkit.createInventory(holder, InventoryType.SMOKER, Component.text("Smoking Module"));
			case STONECUTTER ->
				Bukkit.createInventory(holder, InventoryType.STONECUTTER, Component.text("Stonecutter Module"));
			case ANVIL -> Bukkit.createInventory(holder, InventoryType.ANVIL, Component.text("Anvil Module"));
			case DROPPER -> Bukkit.createInventory(holder, InventoryType.DROPPER, Component.text("Dropper Module"));
			case HOPPER -> Bukkit.createInventory(holder, InventoryType.HOPPER, Component.text("Hopper Module"));
			default -> Bukkit.createInventory(holder, 27, Component.text("Module"));
		};

		holder.setInventory(inv);

		// Load saved state into this inventory (from the backpack row + moduleStates
		// map)
		BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
		byte[] state = data.moduleStates().get(moduleId);
		if (state == null) {
			player.openInventory(inv);
			return;
		}

		// Everything uses ItemStackCodec bytes (gzipped)
		// Note: Furnace types should not reach this code path as they use
		// MenuType-based views
		ItemStack[] saved = ItemStackCodec.fromBytes(state);
		int limit = Math.min(inv.getSize(), saved.length);
		for (int i = 0; i < limit; i++) {
			inv.setItem(i, saved[i]);
		}

		// Result/output slots are derived; never trust persisted values.
		if (screenType == ScreenType.CRAFTING) {
			inv.setItem(0, null);
			craftingModule.updateResult(plugin.recipes(), player, inv);
		}
		if (screenType == ScreenType.STONECUTTER) {
			if (inv.getSize() > 1) {
				inv.setItem(1, null);
				stonecutterModule.updateResult(inv);
			}
		}
		if (screenType == ScreenType.SMITHING) {
			if (inv.getSize() > 3) {
				inv.setItem(3, null);
				smithingModule.updateResult(inv);
			}
		}

		player.openInventory(inv);

	}

}
