package io.github.tootertutor.ModularPacks.listeners.backpack;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;

/**
 * Recognizes the outside-click encoding independently of cursor/view safety
 * checks.
 */
final class BackpackInventoryGesture {
    static boolean isOutsideLeftClick(int rawSlot, ClickType click) {
        // Paper 1.21.10 and 26.3 report a PICKUP packet for slot -999 as LEFT.
        // WINDOW_BORDER_LEFT is also accepted for implementations using that encoding.
        return rawSlot == InventoryView.OUTSIDE
                && (click == ClickType.LEFT || click == ClickType.WINDOW_BORDER_LEFT);
    }

    private BackpackInventoryGesture() {
    }
}
