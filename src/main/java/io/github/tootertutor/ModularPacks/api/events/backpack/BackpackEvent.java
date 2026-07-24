package io.github.tootertutor.ModularPacks.api.events.backpack;

import java.util.UUID;

import org.bukkit.event.Event;

/**
 * Base event for backpack actions.
 */
public abstract class BackpackEvent extends Event {

    private final UUID backpackId;
    private final String backpackType;

    protected BackpackEvent(UUID backpackId, String backpackType) {
        this.backpackId = backpackId;
        this.backpackType = backpackType;
    }

    public UUID getBackpackId() {
        return backpackId;
    }

    public String getBackpackType() {
        return backpackType;
    }
}
