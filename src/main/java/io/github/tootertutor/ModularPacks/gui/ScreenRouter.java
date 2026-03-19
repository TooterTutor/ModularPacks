package io.github.tootertutor.ModularPacks.gui;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.ModularPacksAPI;
import io.github.tootertutor.ModularPacks.api.modules.ModuleRegistry;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.modules.anvil.AnvilModule;
import io.github.tootertutor.ModularPacks.modules.crafting.CraftingModule;
import io.github.tootertutor.ModularPacks.modules.furnace.FurnaceModule;
import io.github.tootertutor.ModularPacks.modules.smithing.SmithingModule;
import io.github.tootertutor.ModularPacks.modules.stonecutter.StonecutterModule;
import io.github.tootertutor.ModularPacks.screens.core.BackpackScreenRegistry;

public final class ScreenRouter {

	private final ModularPacksPlugin plugin;

	private final SmithingModule smithingModule;
	private final StonecutterModule stonecutterModule;
	private final CraftingModule craftingModule;
	private final AnvilModule anvilModule;
	private final FurnaceModule furnaceModule;
	private final BackpackScreenRegistry screenRegistry;
	private final Map<ScreenType, Consumer<ScreenOpenRequest>> specializedOpeners;

	private record ScreenOpenRequest(Player player, UUID backpackId, String backpackType, UUID moduleId,
			ScreenType screenType) {
	}

	public ScreenRouter(ModularPacksPlugin plugin) {
		this.plugin = plugin;
		this.smithingModule = new SmithingModule();
		this.stonecutterModule = new StonecutterModule();
		this.craftingModule = new CraftingModule();
		this.anvilModule = new AnvilModule();
		this.furnaceModule = new FurnaceModule();
		this.screenRegistry = new BackpackScreenRegistry(plugin);
		this.specializedOpeners = buildSpecializedOpeners();

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

	private Map<ScreenType, Consumer<ScreenOpenRequest>> buildSpecializedOpeners() {
		Map<ScreenType, Consumer<ScreenOpenRequest>> openers = new EnumMap<>(ScreenType.class);

		openers.put(ScreenType.ANVIL,
				request -> openNextTick(() -> anvilModule.open(plugin, request.player(), request.backpackId(),
						request.backpackType(), request.moduleId())));
		openers.put(ScreenType.CRAFTING,
				request -> openNextTick(() -> craftingModule.open(plugin, request.player(), request.backpackId(),
						request.backpackType(), request.moduleId())));
		openers.put(ScreenType.SMITHING,
				request -> openNextTick(() -> smithingModule.open(plugin, request.player(), request.backpackId(),
						request.backpackType(), request.moduleId())));
		openers.put(ScreenType.STONECUTTER,
				request -> openNextTick(() -> stonecutterModule.open(plugin, request.player(), request.backpackId(),
						request.backpackType(), request.moduleId())));

		Consumer<ScreenOpenRequest> furnaceLikeOpener = request -> openNextTick(
				() -> furnaceModule.open(plugin, request.player(), request.backpackId(), request.backpackType(),
						request.moduleId(), request.screenType()));
		openers.put(ScreenType.SMELTING, furnaceLikeOpener);
		openers.put(ScreenType.BLASTING, furnaceLikeOpener);
		openers.put(ScreenType.SMOKING, furnaceLikeOpener);

		return openers;
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

		ScreenOpenRequest request = new ScreenOpenRequest(player, backpackId, backpackType, moduleId, screenType);
		Consumer<ScreenOpenRequest> opener = specializedOpeners.get(screenType);
		if (opener != null) {
			opener.accept(request);
			return;
		}

		// Holder so listeners can identify and persist this module screen
		ModuleScreenHolder holder = new ModuleScreenHolder(backpackId, backpackType, moduleId, screenType);
		Inventory inv = screenRegistry.createInventory(holder);

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
