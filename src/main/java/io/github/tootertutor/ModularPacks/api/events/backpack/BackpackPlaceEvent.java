package io.github.tootertutor.ModularPacks.api.events.backpack;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired before a backpack is placed in the world.
 */
public final class BackpackPlaceEvent extends BackpackEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final ItemStack backpackItem;
    private boolean cancelled;

    public BackpackPlaceEvent(Player player, UUID backpackId, String backpackType, Location location,
            ItemStack backpackItem) {
        super(backpackId, backpackType);
        this.player = player;
        this.location = location.clone();
        this.backpackItem = backpackItem == null ? null : backpackItem.clone();
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location.clone();
    }

    public ItemStack getBackpackItem() {
        return backpackItem == null ? null : backpackItem.clone();
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
