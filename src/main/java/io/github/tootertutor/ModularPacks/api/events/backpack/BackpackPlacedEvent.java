package io.github.tootertutor.ModularPacks.api.events.backpack;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired after a backpack has been placed in the world.
 */
public final class BackpackPlacedEvent extends BackpackEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;

    public BackpackPlacedEvent(Player player, UUID backpackId, String backpackType, Location location) {
        super(backpackId, backpackType);
        this.player = player;
        this.location = location.clone();
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location.clone();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
