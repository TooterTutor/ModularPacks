package io.github.tootertutor.ModularPacks.api.events.backpack;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired after a placed backpack is picked up.
 */
public final class BackpackPickedUpEvent extends BackpackEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final BackpackPickupCause cause;
    private final ItemStack backpackItem;

    public BackpackPickedUpEvent(Player player, UUID backpackId, String backpackType, Location location,
            BackpackPickupCause cause, ItemStack backpackItem) {
        super(backpackId, backpackType);
        this.player = player;
        this.location = location.clone();
        this.cause = cause;
        this.backpackItem = backpackItem == null ? null : backpackItem.clone();
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

    public ItemStack getBackpackItem() {
        return backpackItem == null ? null : backpackItem.clone();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
