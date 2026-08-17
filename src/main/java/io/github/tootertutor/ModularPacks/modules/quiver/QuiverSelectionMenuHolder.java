package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Non-storage projection used to choose an exact Quiver identity. */
public final class QuiverSelectionMenuHolder implements InventoryHolder {

    private final UUID backpackId;
    private final String backpackType;
    private final UUID moduleId;
    private final Map<Integer, ItemStack> choices = new HashMap<>();
    private Inventory inventory;

    public QuiverSelectionMenuHolder(UUID backpackId, String backpackType, UUID moduleId) {
        this.backpackId = backpackId;
        this.backpackType = backpackType;
        this.moduleId = moduleId;
    }

    public UUID backpackId() {
        return backpackId;
    }

    public String backpackType() {
        return backpackType;
    }

    public UUID moduleId() {
        return moduleId;
    }

    public void clearChoices() {
        choices.clear();
    }

    public void choice(int slot, ItemStack prototype) {
        ItemStack normalized = prototype.clone();
        normalized.setAmount(1);
        choices.put(slot, normalized);
    }

    public ItemStack choice(int slot) {
        ItemStack prototype = choices.get(slot);
        return prototype == null ? null : prototype.clone();
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
