package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.storage.BackpackStorage;
import io.github.tootertutor.ModularPacks.storage.BackpackStorageCodec;
import io.github.tootertutor.ModularPacks.storage.BackpackStorageService;
import io.github.tootertutor.ModularPacks.storage.StackIdentityService;
import io.github.tootertutor.ModularPacks.storage.StoredStack;

/** Selection over, and direct extraction from, authoritative backpack storage. */
public final class QuiverAmmoService {

    private final BackpackStorageService storageService;
    private final StackIdentityService identity;

    /** Compatibility constructor retained for early Phase 5 integrations. */
    public QuiverAmmoService(StackIdentityService identity) {
        this(new BackpackStorageService(new BackpackStorageCodec(), identity));
    }

    public QuiverAmmoService(BackpackStorageService storageService) {
        if (storageService == null) {
            throw new IllegalArgumentException("storageService cannot be null");
        }
        this.storageService = storageService;
        this.identity = storageService.identity();
    }

    public static boolean isSupported(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.ARROW || type == Material.SPECTRAL_ARROW || type == Material.TIPPED_ARROW;
    }

    public List<AmmoOption> availableOptions(BackpackStorage storage) {
        if (storage == null) {
            return List.of();
        }
        List<AmmoOption> options = new ArrayList<>();
        for (int slot = 0; slot < storage.size(); slot++) {
            StoredStack stored = storage.get(slot);
            if (stored == null || !isSupported(stored.prototype())) {
                continue;
            }
            int existingIndex = findOption(options, stored.prototype());
            if (existingIndex < 0) {
                options.add(new AmmoOption(stored.prototype(), stored.count()));
            } else {
                AmmoOption existing = options.get(existingIndex);
                options.set(existingIndex,
                        new AmmoOption(existing.prototype(), addSaturated(existing.count(), stored.count())));
            }
        }
        return List.copyOf(options);
    }

    public ItemStack select(QuiverSelection selection, BackpackStorage storage) {
        QuiverSelection value = selection == null ? QuiverSelection.auto() : selection;
        if (value.mode() == QuiverSelectionMode.EXACT) {
            ItemStack exact = value.selectedPrototype();
            return exact != null && count(storage, exact) > 0 ? exact : null;
        }
        List<AmmoOption> options = availableOptions(storage);
        return options.isEmpty() ? null : options.get(0).prototype();
    }

    public long count(BackpackStorage storage, ItemStack prototype) {
        if (storage == null || !isSupported(prototype)) {
            return 0;
        }
        return storageService.countMatching(storage, prototype);
    }

    public boolean consumeOne(BackpackStorage storage, ItemStack prototype) {
        if (storage == null || !isSupported(prototype)) {
            return false;
        }
        return storageService.extractMatching(storage, prototype, 1) == 1;
    }

    private int findOption(List<AmmoOption> options, ItemStack prototype) {
        for (int index = 0; index < options.size(); index++) {
            if (identity.sameIdentity(options.get(index).prototype(), prototype)) {
                return index;
            }
        }
        return -1;
    }

    private static long addSaturated(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    public record AmmoOption(ItemStack storedPrototype, long count) {
        public AmmoOption {
            if (!isSupported(storedPrototype) || count <= 0) {
                throw new IllegalArgumentException("Quiver option must contain supported ammunition");
            }
            storedPrototype = storedPrototype.clone();
            storedPrototype.setAmount(1);
        }

        public ItemStack prototype() {
            return storedPrototype.clone();
        }
    }
}
