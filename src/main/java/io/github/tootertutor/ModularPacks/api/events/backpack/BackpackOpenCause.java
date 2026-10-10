package io.github.tootertutor.ModularPacks.api.events.backpack;

/**
 * Why a backpack open action was requested.
 */
public enum BackpackOpenCause {
    ITEM_USE,
    INVENTORY_GESTURE,
    EQUIPPED_HOTKEY,
    PLACED_INTERACT,
    COMMAND,
    API
}
