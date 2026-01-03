package io.github.tootertutor.ModularPacks.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RecipePreviewHolder implements InventoryHolder {

    private final RecipePreviewUi.Kind kind;
    private Inventory inventory;

    public RecipePreviewHolder(RecipePreviewUi.Kind kind) {
        this.kind = kind == null ? RecipePreviewUi.Kind.CRAFTING : kind;
    }

    public RecipePreviewUi.Kind kind() {
        return kind;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

