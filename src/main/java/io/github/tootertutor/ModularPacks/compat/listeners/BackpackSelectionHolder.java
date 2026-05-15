package io.github.tootertutor.ModularPacks.compat.listeners;

import java.util.List;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class BackpackSelectionHolder implements InventoryHolder {

    private Inventory inventory;
    private final List<ItemStack> backpacks;
    private final int currentPage;

    public BackpackSelectionHolder(List<ItemStack> backpacks, int currentPage) {
        this.inventory = null;
        this.backpacks = backpacks;
        this.currentPage = currentPage;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public List<ItemStack> getBackpacks() {
        return backpacks;
    }

    public int getCurrentPage() {
        return currentPage;
    }
}
