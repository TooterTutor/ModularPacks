package io.github.tootertutor.ModularPacks.api.events.backpack;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import io.github.tootertutor.ModularPacks.data.PlacedBackpack;

/**
 * Fired before a placed backpack is picked up.
 */
public final class BackpackPickupEvent extends BackpackEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final BackpackPickupCause cause;
    private final PlacedBackpack placedBackpack;
    private boolean cancelled;

    public BackpackPickupEvent(Player player, UUID backpackId, String backpackType, Location location,
            BackpackPickupCause cause, PlacedBackpack placedBackpack) {
        super(backpackId, backpackType);
        this.player = player;
        this.location = location.clone();
        this.cause = cause;
        this.placedBackpack = placedBackpack;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location.clone();
    }

    public BackpackPickupCause getCause() {
        return cause;
    }

    public PlacedBackpack getPlacedBackpack() {
        return placedBackpack;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
