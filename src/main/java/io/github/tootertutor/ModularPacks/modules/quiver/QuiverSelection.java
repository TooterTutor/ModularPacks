package io.github.tootertutor.ModularPacks.modules.quiver;

import org.bukkit.inventory.ItemStack;

/** Immutable, identity-based Quiver selection. */
public final class QuiverSelection {

    private final QuiverSelectionMode mode;
    private final ItemStack selectedPrototype;

    private QuiverSelection(QuiverSelectionMode mode, ItemStack selectedPrototype) {
        this.mode = mode;
        if (selectedPrototype == null) {
            this.selectedPrototype = null;
        } else {
            this.selectedPrototype = selectedPrototype.clone();
            this.selectedPrototype.setAmount(1);
        }
    }

    public static QuiverSelection auto() {
        return new QuiverSelection(QuiverSelectionMode.AUTO, null);
    }

    public static QuiverSelection exact(ItemStack prototype) {
        if (!QuiverAmmoService.isSupported(prototype)) {
            throw new IllegalArgumentException("prototype must be a supported arrow");
        }
        return new QuiverSelection(QuiverSelectionMode.EXACT, prototype);
    }

    public QuiverSelectionMode mode() {
        return mode;
    }

    public ItemStack selectedPrototype() {
        return selectedPrototype == null ? null : selectedPrototype.clone();
    }
}
