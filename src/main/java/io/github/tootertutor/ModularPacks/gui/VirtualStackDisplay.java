package io.github.tootertutor.ModularPacks.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.ModularPacks.storage.BackpackStorageService;
import io.github.tootertutor.ModularPacks.storage.StoredStack;
import io.github.tootertutor.ModularPacks.util.Text;
import net.kyori.adventure.text.Component;

/** Creates non-authoritative, vanilla-safe GUI clones for logical stacks. */
public final class VirtualStackDisplay {

    private VirtualStackDisplay() {
    }

    public static ItemStack render(BackpackStorageService storageService, StoredStack stored,
            long logicalCapacity, String loreFormat) {
        if (storageService == null || stored == null) {
            throw new IllegalArgumentException("storageService and stored cannot be null");
        }
        ItemStack prototype = stored.prototype();
        int displayAmount = Math.toIntExact(Math.min(stored.count(), prototype.getMaxStackSize()));
        ItemStack display = storageService.materialize(prototype, displayAmount);

        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            String format = loreFormat == null || loreFormat.isBlank()
                    ? "&7Stored: &f{count} &7/ &f{capacity}"
                    : loreFormat;
            lore.add(Text.c(format
                    .replace("{count}", Long.toString(stored.count()))
                    .replace("{capacity}", Long.toString(logicalCapacity))));
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }
}
