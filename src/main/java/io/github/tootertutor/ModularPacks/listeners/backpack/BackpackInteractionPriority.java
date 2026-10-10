package io.github.tootertutor.ModularPacks.listeners.backpack;

/**
 * Resolves explicit backpack gestures without predicting vanilla item behavior.
 */
final class BackpackInteractionPriority {
    enum Item {
        EMPTY, OTHER, BACKPACK
    }

    enum Action {
        VANILLA, PLACED_BACKPACK, OPEN_HELD, PLACE_HELD, NONE
    }

    static Action resolve(Item main, Item off, boolean mainHand, boolean sneaking,
            boolean block, boolean placedBackpack) {
        // Sneaking bypasses a placed backpack when either hand holds an item.
        // Empty hands explicitly request pickup instead.
        if (placedBackpack && (!sneaking || (main == Item.EMPTY && off == Item.EMPTY))) {
            return Action.PLACED_BACKPACK;
        }
        // A pair of held backpacks belongs to the main hand, never both hands.
        if (!mainHand && main == Item.BACKPACK) {
            return Action.NONE;
        }
        Item held = mainHand ? main : off;
        if (held == Item.BACKPACK) {
            return sneaking && block ? Action.PLACE_HELD : Action.OPEN_HELD;
        }
        // Vanilla decides whether to use the main hand or try the offhand.
        return Action.VANILLA;
    }

    private BackpackInteractionPriority() {
    }
}
