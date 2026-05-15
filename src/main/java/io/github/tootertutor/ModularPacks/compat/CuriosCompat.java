package io.github.tootertutor.ModularPacks.compat;

import java.util.Collections;
import java.util.List;

import org.bg52.curiospaper.CuriosPaper;
import org.bg52.curiospaper.api.CuriosPaperAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;

public class CuriosCompat {

    private final ModularPacksPlugin plugin;
    private CuriosPaperAPI curiosAPI;
    private boolean slotRegistered = false;

    public CuriosCompat(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            CuriosPaper curiosPaper = CuriosPaper.getInstance();
            if (curiosPaper == null) {
                plugin.getLogger().warning("CuriosPaper instance is null");
                curiosAPI = null;
                return;
            }

            curiosAPI = curiosPaper.getCuriosPaperAPI();
            if (curiosAPI == null) {
                plugin.getLogger().warning("CuriosPaper API is null");
                return;
            }

            plugin.getLogger().info("CuriosPaper API initialized");
        } catch (Exception e) {
            curiosAPI = null;
            plugin.getLogger().warning("CuriosPaper not available; compatibility disabled.");
        }
    }

    public void registerBackpackType(BackpackTypeDef backpackType) {
        if (curiosAPI == null || backpackType == null) {
            plugin.getLogger().warning("Cannot register backpack type - curiosAPI is null or backpackType is null");
            return;
        }

        try {
            // Register slot using the first available backpack type definition.
            if (!slotRegistered) {
                registerSlotFromBackpackType(backpackType);
                slotRegistered = true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering backpack type: " + e.getMessage());
        }
    }

    public void registerBackpackTypes(Iterable<BackpackTypeDef> backpackTypes) {
        if (backpackTypes == null) {
            return;
        }

        for (BackpackTypeDef backpackType : backpackTypes) {
            registerBackpackType(backpackType);
            if (slotRegistered) {
                return;
            }
        }
    }

    private void registerSlotFromBackpackType(BackpackTypeDef backpackType) {
        try {
            Integer customModelData = backpackType.customModelData() > 0 ? backpackType.customModelData() : null;

            plugin.getLogger().info("Registering backpack slot with material: " + backpackType.outputMaterial());

            curiosAPI.registerSlot(
                    "back",
                    "Backpack",
                    backpackType.outputMaterial(),
                    null,
                    customModelData,
                    1,
                    backpackType.lore());

            plugin.getLogger().info("Successfully registered backpack slot");
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering slot: " + e.getMessage());
        }
    }

    public boolean isBackpackSlotRegistered() {
        return curiosAPI != null && slotRegistered;
    }

    public boolean isApiReady() {
        return curiosAPI != null;
    }

    public List<ItemStack> getEquippedItems(Player player, String slotType) {
        if (curiosAPI == null || player == null || slotType == null) {
            return Collections.emptyList();
        }

        try {
            List<ItemStack> equipped = curiosAPI.getEquippedItems(player, slotType);
            return equipped != null ? equipped : Collections.emptyList();
        } catch (Exception e) {
            plugin.getLogger().warning("Error reading equipped Curios items: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public void setEquippedItem(Player player, String slotType, int slotIndex, ItemStack item) {
        if (curiosAPI == null || player == null || slotType == null || slotIndex < 0 || item == null) {
            return;
        }

        try {
            curiosAPI.setEquippedItem(player, slotType, slotIndex, item);
        } catch (Exception e) {
            plugin.getLogger().warning("Error writing equipped Curios item: " + e.getMessage());
        }
    }
}
