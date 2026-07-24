package io.github.tootertutor.ModularPacks.api.events.backpack;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired after a backpack UI has been opened.
 */
public final class BackpackOpenedEvent extends BackpackEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final BackpackOpenCause cause;
    private final Location placedLocation;

    public BackpackOpenedEvent(Player player, UUID backpackId, String backpackType, BackpackOpenCause cause,
            Location placedLocation) {
        super(backpackId, backpackType);
        this.player = player;
        this.cause = cause;
        this.placedLocation = placedLocation == null ? null : placedLocation.clone();
    }

    public Player getPlayer() {
        return player;
    }

    public BackpackOpenCause getCause() {
        return cause;
    }

    public Location getPlacedLocation() {
        return placedLocation == null ? null : placedLocation.clone();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
