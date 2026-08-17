package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/** Exact logical ammunition source chosen when a weapon-use lease begins. */
public record QuiverSource(
        UUID backpackId,
        String backpackType,
        UUID moduleId,
        QuiverSelection selection,
        ItemStack storedPrototype) {

    public QuiverSource {
        if (backpackId == null || backpackType == null || moduleId == null
                || selection == null || !QuiverAmmoService.isSupported(storedPrototype)) {
            throw new IllegalArgumentException("Quiver source is incomplete");
        }
        storedPrototype = storedPrototype.clone();
        storedPrototype.setAmount(1);
    }

    public ItemStack prototype() {
        return storedPrototype.clone();
    }
}
