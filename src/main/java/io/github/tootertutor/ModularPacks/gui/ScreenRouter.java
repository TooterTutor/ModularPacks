package io.github.tootertutor.ModularPacks.gui;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

		// Determine inventory title based on module type and screen type
		Component title = getInventoryTitle(screenType, backpackId, moduleId);

		Inventory inv = switch (screenType) {
			case CRAFTING -> Bukkit.createInventory(holder, InventoryType.WORKBENCH, title);
			case SMITHING -> Bukkit.createInventory(holder, InventoryType.SMITHING, title);
			case SMELTING -> Bukkit.createInventory(holder, InventoryType.FURNACE, title);
			case BLASTING -> Bukkit.createInventory(holder, InventoryType.BLAST_FURNACE, title);
			case SMOKING -> Bukkit.createInventory(holder, InventoryType.SMOKER, title);
			case STONECUTTER -> Bukkit.createInventory(holder, InventoryType.STONECUTTER, title);
			case ANVIL -> Bukkit.createInventory(holder, InventoryType.ANVIL, title);
			case DROPPER -> Bukkit.createInventory(holder, InventoryType.DROPPER, title);
			case HOPPER -> Bukkit.createInventory(holder, InventoryType.HOPPER, title);
			default -> Bukkit.createInventory(holder, 27, title);
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

	/**
	 * Determine the appropriate inventory title based on module type and screen
	 * type.
	 * For DROPPER/HOPPER screens, resolve the module type and filter mode.
	 */
	private Component getInventoryTitle(ScreenType screenType, UUID backpackId, UUID moduleId) {
		String title = switch (screenType) {
			case CRAFTING -> "Crafting Module";
			case SMITHING -> "Smithing Module";
			case SMELTING -> "Smelting Module";
			case BLASTING -> "Blasting Module";
			case SMOKING -> "Smoking Module";
			case STONECUTTER -> "Stonecutter Module";
			case ANVIL -> "Anvil Module";
			case DROPPER -> getTitleForDropper(backpackId, moduleId);
			case HOPPER -> getTitleForHopper(backpackId, moduleId);
			default -> "Module";
		};
		return Component.text(title);
	}

	/**
	 * Get title for DROPPER screens based on module type and filter mode.
	 * DROPPER is used by: Feeding, Void, Magnet, Jukebox, Restock (whitelist)
	 */
	private String getTitleForDropper(UUID backpackId, UUID moduleId) {
		String moduleType = resolveModuleType(backpackId, moduleId);
		if (moduleType == null || moduleType.isEmpty())
			return "Configuration";

		// Feeding uses filter mode (whitelist/blacklist)
		if ("Feeding".equalsIgnoreCase(moduleType)) {
			String filterMode = resolveFilterMode(backpackId, moduleId);
			if ("BLACKLIST".equalsIgnoreCase(filterMode))
				return "Feeding Blacklist";
			return "Feeding Whitelist";
		}

		// Void uses whitelist only (for safety - never blacklist)
		if ("Void".equalsIgnoreCase(moduleType)) {
			return "Void Whitelist";
		}

		// Magnet uses filter mode (whitelist/blacklist)
		if ("Magnet".equalsIgnoreCase(moduleType)) {
			String filterMode = resolveFilterMode(backpackId, moduleId);
			if ("BLACKLIST".equalsIgnoreCase(filterMode))
				return "Magnet Blacklist";
			return "Magnet Whitelist";
		}

		// Restock uses whitelist only
		if ("Restock".equalsIgnoreCase(moduleType))
			return "Restock Whitelist";

		// Jukebox uses actual inventory for discs
		if ("Jukebox".equalsIgnoreCase(moduleType))
			return "Jukebox Playlist";

		return moduleType + " Configuration";
	}

	/**
	 * Get title for HOPPER screens based on module type.
	 * HOPPER is used by: Restock (threshold value)
	 */
	private String getTitleForHopper(UUID backpackId, UUID moduleId) {
		String moduleType = resolveModuleType(backpackId, moduleId);
		if (moduleType == null || moduleType.isEmpty())
			return "Configuration";

		if ("Restock".equalsIgnoreCase(moduleType))
			return "Restock Threshold";

		return moduleType + " Configuration";
	}

	/**
	 * Resolve the module type from the installed module snapshot.
	 */
	private String resolveModuleType(UUID backpackId, UUID moduleId) {
		try {
			BackpackData data = plugin.repo().loadOrCreate(backpackId, null);
			if (data == null)
				return null;

			byte[] snap = data.installedSnapshots().get(moduleId);
			if (snap == null || snap.length == 0)
				return null;

			ItemStack[] arr = ItemStackCodec.fromBytes(snap);
			if (arr.length == 0 || arr[0] == null || !arr[0].hasItemMeta())
				return null;

			ItemMeta meta = arr[0].getItemMeta();
			if (meta == null)
				return null;

			return meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE,
					org.bukkit.persistence.PersistentDataType.STRING);
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Resolve the filter mode (WHITELIST or BLACKLIST) from the module.
	 */
	private String resolveFilterMode(UUID backpackId, UUID moduleId) {
		try {
			BackpackData data = plugin.repo().loadOrCreate(backpackId, null);
			if (data == null)
				return "WHITELIST";

			byte[] snap = data.installedSnapshots().get(moduleId);
			if (snap == null || snap.length == 0)
				return "WHITELIST";

			ItemStack[] arr = ItemStackCodec.fromBytes(snap);
			if (arr.length == 0 || arr[0] == null || !arr[0].hasItemMeta())
				return "WHITELIST";

			ItemMeta meta = arr[0].getItemMeta();
			if (meta == null)
				return "WHITELIST";

			String mode = meta.getPersistentDataContainer().get(plugin.keys().MODULE_FILTER_MODE,
					org.bukkit.persistence.PersistentDataType.STRING);
			return mode != null ? mode : "WHITELIST";
		} catch (Exception ex) {
			return "WHITELIST";
		}
	}

}
