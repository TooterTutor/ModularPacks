package io.github.tootertutor.ModularPacks.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represents a backpack placed in the world as a physical block.
 * Stores location, owner, and links to the backpack data.
 */
public final class PlacedBackpack {

    private final UUID backpackId;
    private final String backpackType;
    private final Location location;
    private final UUID ownerId; // Player who placed it
    private final String ownerName;
    private final List<String> modelDataStrings;
    private final List<Integer> modelDataColors;
    private long lastTickTime;

    public PlacedBackpack(UUID backpackId, String backpackType, Location location, UUID ownerId, String ownerName) {
        this(backpackId, backpackType, location, ownerId, ownerName, List.of(), List.of());
    }

    public PlacedBackpack(UUID backpackId, String backpackType, Location location, UUID ownerId, String ownerName,
            List<String> modelDataStrings, List<Integer> modelDataColors) {
        this.backpackId = backpackId;
        this.backpackType = backpackType;
        this.location = location;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.modelDataStrings = modelDataStrings == null ? List.of() : List.copyOf(modelDataStrings);
        this.modelDataColors = modelDataColors == null ? List.of() : List.copyOf(modelDataColors);
        this.lastTickTime = System.currentTimeMillis();
    }

    public UUID backpackId() {
        return backpackId;
    }

    public String backpackType() {
        return backpackType;
    }

    public Location location() {
        return location;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public List<String> modelDataStrings() {
        return new ArrayList<>(modelDataStrings);
    }

    public List<Integer> modelDataColors() {
        return new ArrayList<>(modelDataColors);
    }

    public long lastTickTime() {
        return lastTickTime;
    }

    public void updateTickTime() {
        this.lastTickTime = System.currentTimeMillis();
    }

    /**
     * Check if the location is still valid and the block exists.
     */
    public boolean isValid() {
        if (location == null || location.getWorld() == null)
            return false;
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
            return false;
        return true;
    }

    /**
     * Get a unique key for this placed backpack based on location.
     */
    public String locationKey() {
        return location.getWorld().getName() + ";" +
                location.getBlockX() + ";" +
                location.getBlockY() + ";" +
                location.getBlockZ();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof PlacedBackpack other))
            return false;
        return backpackId.equals(other.backpackId) && locationKey().equals(other.locationKey());
    }

    @Override
    public int hashCode() {
        return backpackId.hashCode() ^ locationKey().hashCode();
    }

    @Override
    public String toString() {
        return "PlacedBackpack{id=" + backpackId + ", type=" + backpackType +
                ", location=" + locationKey() + ", owner=" + ownerName + "}";
    }
}
