package io.github.tootertutor.ModularPacks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.PlacedBackpack;
import io.github.tootertutor.ModularPacks.item.SkullTextureUtil;

/**
 * Manages all placed backpacks in the world.
 * Tracks placement, retrieval, and coordinates ticking for placed backpacks.
 */
public final class PlacedBackpackManager {

    private final ModularPacksPlugin plugin;

    // Location key -> PlacedBackpack
    private final Map<String, PlacedBackpack> placedBackpacks = new HashMap<>();

    // BackpackId -> Set of location keys (for handling multiple placements of same
    // backpack)
    private final Map<UUID, Set<String>> backpackPlacements = new HashMap<>();

    // File for persistence
    private final File dataFile;

    public PlacedBackpackManager(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "placed-backpacks.dat");
        load();
    }

    /**
     * Register a newly placed backpack.
     */
    public boolean place(Location location, UUID backpackId, String backpackType, Player placer) {
        String key = locationKey(location);

        if (placedBackpacks.containsKey(key)) {
            plugin.getLogger().warning("Attempted to place backpack at occupied location: " + key);
            return false;
        }

        PlacedBackpack placed = new PlacedBackpack(
                backpackId,
                backpackType,
                location.clone(),
                placer.getUniqueId(),
                placer.getName());

        placedBackpacks.put(key, placed);
        backpackPlacements.computeIfAbsent(backpackId, k -> new HashSet<>()).add(key);

        // Set the block to a player head with the backpack texture and proper rotation
        setBackpackBlockWithRotation(location, backpackType, placer);

        save();
        return true;
    }

    /**
     * Remove a placed backpack from the world.
     * Returns the PlacedBackpack data if it existed.
     */
    public PlacedBackpack remove(Location location) {
        String key = locationKey(location);
        PlacedBackpack placed = placedBackpacks.remove(key);

        if (placed != null) {
            Set<String> locations = backpackPlacements.get(placed.backpackId());
            if (locations != null) {
                locations.remove(key);
                if (locations.isEmpty()) {
                    backpackPlacements.remove(placed.backpackId());
                }
            }
            save();
        }

        return placed;
    }

    /**
     * Get the placed backpack at a specific location.
     */
    public PlacedBackpack getAt(Location location) {
        return placedBackpacks.get(locationKey(location));
    }

    /**
     * Check if a backpack is placed at a location.
     */
    public boolean isPlacedAt(Location location) {
        return placedBackpacks.containsKey(locationKey(location));
    }

    /**
     * Get all placed backpacks.
     */
    public Map<String, PlacedBackpack> getAllPlaced() {
        return new HashMap<>(placedBackpacks);
    }

    /**
     * Check if a backpack is currently placed anywhere in the world.
     */
    public boolean isBackpackPlaced(UUID backpackId) {
        Set<String> locations = backpackPlacements.get(backpackId);
        return locations != null && !locations.isEmpty();
    }

    /**
     * Get all locations where a specific backpack is placed.
     */
    public Set<Location> getPlacementLocations(UUID backpackId) {
        Set<Location> locations = new HashSet<>();
        Set<String> keys = backpackPlacements.get(backpackId);
        if (keys != null) {
            for (String key : keys) {
                PlacedBackpack placed = placedBackpacks.get(key);
                if (placed != null && placed.isValid()) {
                    locations.add(placed.location());
                }
            }
        }
        return locations;
    }

    /**
     * Tick all placed backpacks.
     * Called by ModuleEngineService.
     */
    public void tickPlacedBackpacks(Set<UUID> openModuleIds, Set<UUID> openBackpackIds) {
        // Will be implemented to work with the existing ticking system
        for (PlacedBackpack placed : new HashSet<>(placedBackpacks.values())) {
            if (!placed.isValid()) {
                continue;
            }

            // Placed backpacks tick even when no player is nearby
            // This will be integrated with ModuleEngineService
            placed.updateTickTime();
        }
    }

    /**
     * Clean up invalid placed backpacks (world unloaded, etc.).
     */
    public void cleanupInvalid() {
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, PlacedBackpack> entry : placedBackpacks.entrySet()) {
            if (!entry.getValue().isValid()) {
                toRemove.add(entry.getKey());
            }
        }

        for (String key : toRemove) {
            PlacedBackpack placed = placedBackpacks.remove(key);
            if (placed != null) {
                Set<String> locations = backpackPlacements.get(placed.backpackId());
                if (locations != null) {
                    locations.remove(key);
                    if (locations.isEmpty()) {
                        backpackPlacements.remove(placed.backpackId());
                    }
                }
            }
        }

        if (!toRemove.isEmpty()) {
            save();
        }
    }

    /**
     * Apply skull texture to an existing player head block.
     */
    private void applySkullTexture(Block block, String backpackType) {
        if (block.getType() != Material.PLAYER_HEAD) {
            return;
        }

        BackpackTypeDef typeDef = plugin.cfg().findType(backpackType);
        if (typeDef == null) {
            return;
        }

        BlockState state = block.getState();
        if (state instanceof Skull skull) {
            String skullData = typeDef.skullData();
            if (skullData != null && !skullData.isBlank()) {
                SkullTextureUtil.applyBase64Texture(skull, skullData);
                state.update(true, false);
            }
        }
    }

    /**
     * Apply rotation to a player head block.
     */
    private void applyRotation(Location location, String rotationFaceName) {
        Block block = location.getBlock();
        if (block.getType() != Material.PLAYER_HEAD) {
            return;
        }

        try {
            BlockFace face = BlockFace.valueOf(rotationFaceName);
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Rotatable rotatable) {
                rotatable.setRotation(face);
                block.setBlockData(blockData);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid rotation face: " + rotationFaceName);
        }
    }

    /**
     * Set a block to a player head with the backpack's texture.
     */
    private void setBackpackBlock(Location location, String backpackType) {
        BackpackTypeDef typeDef = plugin.cfg().findType(backpackType);
        if (typeDef == null) {
            plugin.getLogger().warning("Unknown backpack type: " + backpackType);
            return;
        }

        Block block = location.getBlock();
        block.setType(Material.PLAYER_HEAD);

        // Apply texture first
        BlockState state = block.getState();
        if (state instanceof Skull skull) {
            String skullData = typeDef.skullData();
            if (skullData != null && !skullData.isBlank()) {
                SkullTextureUtil.applyBase64Texture(skull, skullData);
            }
            state.update(true, false);
        }

        // Then set rotation after texture is applied
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Rotatable rotatable) {
            rotatable.setRotation(BlockFace.NORTH);
            block.setBlockData(blockData);
        }
    }

    /**
     * Set a block to a player head with rotation based on player direction.
     */
    public void setBackpackBlockWithRotation(Location location, String backpackType, Player player) {
        BackpackTypeDef typeDef = plugin.cfg().findType(backpackType);
        if (typeDef == null) {
            plugin.getLogger().warning("Unknown backpack type: " + backpackType);
            return;
        }

        Block block = location.getBlock();
        block.setType(Material.PLAYER_HEAD);

        // Apply texture first
        BlockState state = block.getState();
        if (state instanceof Skull skull) {
            String skullData = typeDef.skullData();
            if (skullData != null && !skullData.isBlank()) {
                SkullTextureUtil.applyBase64Texture(skull, skullData);
            }
            state.update(true, false);
        }

        // Then set rotation after texture is applied
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Rotatable rotatable && player != null) {
            rotatable.setRotation(bestFacing(player));
            block.setBlockData(blockData);
        }
    }

    /**
     * Pick the face closest to the player's yaw (16-way when available).
     */
    private BlockFace bestFacing(Player player) {
        if (player == null)
            return BlockFace.SOUTH;

        Vector look = player.getLocation().getDirection();
        look.setY(0);
        if (look.lengthSquared() == 0)
            return BlockFace.SOUTH;
        look.normalize();

        BlockFace best = BlockFace.SOUTH;
        double bestDot = -2.0;

        for (BlockFace face : BlockFace.values()) {
            Vector f = face.getDirection();
            if (Math.abs(f.getY()) > 0.0001)
                continue; // ignore vertical components
            if (f.lengthSquared() == 0)
                continue;
            f.normalize();
            double dot = look.dot(f);
            if (dot > bestDot) {
                bestDot = dot;
                best = face;
            }
        }

        return best;
    }

    /**
     * Generate a location key.
     */
    private String locationKey(Location loc) {
        return loc.getWorld().getName() + ";" +
                loc.getBlockX() + ";" +
                loc.getBlockY() + ";" +
                loc.getBlockZ();
    }

    /**
     * Save placed backpacks to disk.
     */
    private void save() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            // Create a serializable data structure
            Map<String, SavedPlacement> toSave = new HashMap<>();
            for (Map.Entry<String, PlacedBackpack> entry : placedBackpacks.entrySet()) {
                PlacedBackpack pb = entry.getValue();

                // Capture rotation from the block
                String rotation = "NORTH";
                Block block = pb.location().getBlock();
                if (block.getType() == Material.PLAYER_HEAD) {
                    BlockData blockData = block.getBlockData();
                    if (blockData instanceof Rotatable rotatable) {
                        rotation = rotatable.getRotation().name();
                    }
                }

                toSave.put(entry.getKey(), new SavedPlacement(
                        pb.backpackId().toString(),
                        pb.backpackType(),
                        pb.location().getWorld().getName(),
                        pb.location().getBlockX(),
                        pb.location().getBlockY(),
                        pb.location().getBlockZ(),
                        pb.ownerId().toString(),
                        pb.ownerName(),
                        rotation));
            }

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dataFile))) {
                oos.writeObject(toSave);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save placed backpacks", e);
        }
    }

    /**
     * Load placed backpacks from disk.
     */
    @SuppressWarnings("unchecked")
    private void load() {
        if (!dataFile.exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataFile))) {
            Map<String, SavedPlacement> loaded = (Map<String, SavedPlacement>) ois.readObject();

            for (Map.Entry<String, SavedPlacement> entry : loaded.entrySet()) {
                SavedPlacement sp = entry.getValue();
                World world = Bukkit.getWorld(sp.worldName);
                if (world == null) {
                    plugin.getLogger().warning("World not found for placed backpack: " + sp.worldName);
                    continue;
                }

                Location location = new Location(world, sp.x, sp.y, sp.z);
                UUID backpackId = UUID.fromString(sp.backpackId);
                UUID ownerId = UUID.fromString(sp.ownerId);

                PlacedBackpack placed = new PlacedBackpack(
                        backpackId,
                        sp.backpackType,
                        location,
                        ownerId,
                        sp.ownerName);

                placedBackpacks.put(entry.getKey(), placed);
                backpackPlacements.computeIfAbsent(backpackId, k -> new HashSet<>()).add(entry.getKey());

                // Ensure the block exists and has the correct texture and rotation
                Block block = location.getBlock();
                if (block.getType() != Material.PLAYER_HEAD) {
                    setBackpackBlock(location, sp.backpackType);
                    // Apply saved rotation if available
                    if (sp.rotation != null) {
                        applyRotation(location, sp.rotation);
                    }
                } else {
                    // Block exists but make sure it has the correct texture
                    applySkullTexture(block, sp.backpackType);
                    // Apply saved rotation if available
                    if (sp.rotation != null) {
                        applyRotation(location, sp.rotation);
                    }
                }
            }

            plugin.getLogger().info("Loaded " + placedBackpacks.size() + " placed backpacks");
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load placed backpacks", e);
        }
    }

    /**
     * Save on shutdown.
     */
    public void shutdown() {
        save();
    }

    /**
     * Serializable data structure for persistence.
     */
    private static class SavedPlacement implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        final String backpackId;
        final String backpackType;
        final String worldName;
        final int x, y, z;
        final String ownerId;
        final String ownerName;
        String rotation; // Stored as BlockFace name - transient for old data

        SavedPlacement(String backpackId, String backpackType, String worldName, int x, int y, int z,
                String ownerId, String ownerName, String rotation) {
            this.backpackId = backpackId;
            this.backpackType = backpackType;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.rotation = rotation != null ? rotation : "NORTH";
        }

        // Handle deserialization of old objects without rotation field
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (this.rotation == null) {
                this.rotation = "NORTH";
            }
        }
    }
}
