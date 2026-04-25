package io.github.tootertutor.ModularPacks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.PlacedBackpack;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.CustomModelDataUtil;
import io.github.tootertutor.ModularPacks.item.ModuleModelDataGenerator;
import io.github.tootertutor.ModularPacks.item.SkullTextureUtil;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * Manages all placed backpacks in the world.
 * Tracks placement, retrieval, and coordinates ticking for placed backpacks.
 */
public final class PlacedBackpackManager {

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    // Location key -> PlacedBackpack
    private final Map<String, PlacedBackpack> placedBackpacks = new HashMap<>();

    // BackpackId -> Set of location keys (for handling multiple placements of same
    // backpack)
    private final Map<UUID, Set<String>> backpackPlacements = new HashMap<>();

    // Location key -> spawned render entity UUID
    private final Map<String, UUID> renderEntities = new HashMap<>();

    // File for persistence
    private final File dataFile;

    public PlacedBackpackManager(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
        this.dataFile = new File(plugin.getDataFolder(), "placed-backpacks.dat");
        load();
    }

    /**
     * Register a newly placed backpack.
     */
    public boolean place(Location location, UUID backpackId, String backpackType, Player placer, ItemStack sourceItem) {
        String key = locationKey(location);

        if (placedBackpacks.containsKey(key)) {
            plugin.getLogger().warning("Attempted to place backpack at occupied location: " + key);
            return false;
        }

        List<String> modelDataStrings = List.of();
        List<Integer> modelDataColors = List.of();
        if (sourceItem != null) {
            ItemMeta sourceMeta = sourceItem.getItemMeta();
            if (sourceMeta != null) {
                modelDataStrings = CustomModelDataUtil.getCustomModelDataStrings(sourceMeta);
                List<Color> rawColors = CustomModelDataUtil.getCustomModelDataColors(sourceMeta);
                if (!rawColors.isEmpty()) {
                    List<Integer> rgb = new ArrayList<>(rawColors.size());
                    for (Color c : rawColors) {
                        int color = ((c.getRed() & 0xFF) << 16) | ((c.getGreen() & 0xFF) << 8) | (c.getBlue() & 0xFF);
                        rgb.add(color);
                    }
                    modelDataColors = rgb;
                }
            }
        }

        PlacedBackpack placed = new PlacedBackpack(
                backpackId,
                backpackType,
                location.clone(),
                placer.getUniqueId(),
                placer.getName(),
                modelDataStrings,
                modelDataColors);

        placedBackpacks.put(key, placed);
        backpackPlacements.computeIfAbsent(backpackId, k -> new HashSet<>()).add(key);

        // Set the block to a player head with the backpack texture and proper rotation
        setBackpackBlockWithRotation(location, backpackType, placer);
        spawnOrUpdateRender(placed);

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
        removeRender(key, location);

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
     * Update visual model data for an existing placed backpack and refresh render.
     */
    public boolean updatePlacedVisuals(Location location, ItemStack sourceItem) {
        if (location == null) {
            return false;
        }

        String key = locationKey(location);
        PlacedBackpack existing = placedBackpacks.get(key);
        if (existing == null) {
            return false;
        }

        List<String> modelDataStrings = List.of();
        List<Integer> modelDataColors = List.of();
        if (sourceItem != null) {
            ItemMeta sourceMeta = sourceItem.getItemMeta();
            if (sourceMeta != null) {
                modelDataStrings = CustomModelDataUtil.getCustomModelDataStrings(sourceMeta);
                List<Color> rawColors = CustomModelDataUtil.getCustomModelDataColors(sourceMeta);
                if (!rawColors.isEmpty()) {
                    List<Integer> rgb = new ArrayList<>(rawColors.size());
                    for (Color c : rawColors) {
                        int color = ((c.getRed() & 0xFF) << 16) | ((c.getGreen() & 0xFF) << 8) | (c.getBlue() & 0xFF);
                        rgb.add(color);
                    }
                    modelDataColors = rgb;
                }
            }
        }

        PlacedBackpack updated = new PlacedBackpack(
                existing.backpackId(),
                existing.backpackType(),
                existing.location(),
                existing.ownerId(),
                existing.ownerName(),
                modelDataStrings,
                modelDataColors);

        placedBackpacks.put(key, updated);
        spawnOrUpdateRender(updated);
        save();
        return true;
    }

    /**
     * Update only the module-slot CMD strings on the live ItemDisplay entities for
     * a placed backpack. Does not respawn the entity or touch persisted data.
     */
    public void syncModuleCmd(UUID backpackId, BackpackData data) {
        Set<String> keys = backpackPlacements.get(backpackId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> freshModuleStrings = ModuleModelDataGenerator.generateModuleModelDataStrings(plugin, data);
        for (String key : keys) {
            UUID entityId = renderEntities.get(key);
            if (entityId == null) {
                continue;
            }
            PlacedBackpack placed = placedBackpacks.get(key);
            if (placed == null || placed.location().getWorld() == null) {
                continue;
            }
            Entity entity = placed.location().getWorld().getEntity(entityId);
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            ItemStack current = display.getItemStack();
            if (current == null || current.getType() == Material.AIR || !current.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = current.getItemMeta();
            List<String> existing = new ArrayList<>(CustomModelDataUtil.getCustomModelDataStrings(meta));
            existing.removeIf(s -> s.startsWith("modularpacks.slot."));
            existing.addAll(freshModuleStrings);
            CustomModelDataUtil.setCustomModelDataStrings(meta, existing);
            current.setItemMeta(meta);
            display.setItemStack(current);
        }
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
        // Keep placed backpack records and their ItemDisplay renders in sync with the
        // world.
        for (Map.Entry<String, PlacedBackpack> entry : new HashMap<>(placedBackpacks).entrySet()) {
            String key = entry.getKey();
            PlacedBackpack placed = entry.getValue();
            if (!placed.isValid()) {
                remove(placed.location());
                continue;
            }

            World world = placed.location().getWorld();
            if (world == null) {
                remove(placed.location());
                continue;
            }

            int chunkX = placed.location().getBlockX() >> 4;
            int chunkZ = placed.location().getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                // Keep placement data while chunks are unloaded; render will respawn when
                // chunk is loaded again.
                continue;
            }

            Block block = placed.location().getBlock();
            if (block.getType() != Material.PLAYER_HEAD) {
                dropAndRemoveBrokenPlacement(placed);
                continue;
            }

            ensureRenderPresent(key, placed);

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
            PlacedBackpack placed = placedBackpacks.get(key);
            if (placed != null) {
                remove(placed.location());
            }
        }

        if (!toRemove.isEmpty()) {
            save();
        }
    }

    /**
     * Rebuild/refresh renders for all tracked placed backpacks using current
     * config.
     * This is used after reload so transform changes apply immediately.
     */
    public void refreshAllRenders() {
        for (Map.Entry<String, PlacedBackpack> entry : new HashMap<>(placedBackpacks).entrySet()) {
            PlacedBackpack placed = entry.getValue();
            Location location = placed.location();
            World world = location.getWorld();

            if (world == null) {
                continue;
            }

            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            Block block = location.getBlock();
            if (block.getType() != Material.PLAYER_HEAD) {
                remove(location);
                continue;
            }

            spawnOrUpdateRender(placed);
        }
    }

    /**
     * Ensure placed backpack renders exist for all placements in a loaded chunk.
     * Used on chunk-load to restore ItemDisplays after restarts/unloads.
     */
    public void refreshRendersInChunk(World world, int chunkX, int chunkZ) {
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        for (Map.Entry<String, PlacedBackpack> entry : new HashMap<>(placedBackpacks).entrySet()) {
            String key = entry.getKey();
            PlacedBackpack placed = entry.getValue();
            Location location = placed.location();

            if (location.getWorld() == null || !location.getWorld().equals(world)) {
                continue;
            }

            if ((location.getBlockX() >> 4) != chunkX || (location.getBlockZ() >> 4) != chunkZ) {
                continue;
            }

            Block block = location.getBlock();
            if (block.getType() != Material.PLAYER_HEAD) {
                remove(location);
                continue;
            }

            ensureRenderPresent(key, placed);
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
            }
            skull.customName(Text.c(typeDef.displayName()));
            state.update(true, false);
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
            skull.customName(Text.c(typeDef.displayName()));
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
            skull.customName(Text.c(typeDef.displayName()));
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
                        rotation,
                        pb.modelDataStrings(),
                        pb.modelDataColors()));
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
                        sp.ownerName,
                        sp.modelDataStrings,
                        sp.modelDataColors);

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

                spawnOrUpdateRender(placed);
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
        for (PlacedBackpack placed : new ArrayList<>(placedBackpacks.values())) {
            removeRender(locationKey(placed.location()), placed.location());
        }
        save();
    }

    private void ensureRenderPresent(String key, PlacedBackpack placed) {
        UUID id = renderEntities.get(key);
        if (id != null) {
            Entity existing = placed.location().getWorld().getEntity(id);
            if (existing instanceof ItemDisplay) {
                return;
            }
            renderEntities.remove(key);
        }

        spawnOrUpdateRender(placed);
    }

    private void spawnOrUpdateRender(PlacedBackpack placed) {
        Location base = placed.location();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        String key = locationKey(base);
        removeRender(key, base);

        ItemStack renderItem = createRenderItem(placed);
        if (renderItem == null || renderItem.getType() == Material.AIR) {
            return;
        }

        BlockFace face = getCurrentRotationFace(base);
        float rotX = (float) Math.toRadians(plugin.cfg().placedBackpackRenderRotationX());
        float rotY = (float) Math.toRadians(plugin.cfg().placedBackpackRenderRotationY());
        float rotZ = (float) Math.toRadians(plugin.cfg().placedBackpackRenderRotationZ());
        float baseFacingRadians = baseFacingRadians(face);

        Quaternionf facingRotation = new Quaternionf().rotateY(baseFacingRadians);

        // Rotate configured local offset with facing around block center so X/Z are
        // relative to backpack direction.
        Vector3f localOffset = new Vector3f(
                (float) plugin.cfg().placedBackpackRenderOffsetX() - 0.5f,
                (float) plugin.cfg().placedBackpackRenderOffsetY(),
                (float) plugin.cfg().placedBackpackRenderOffsetZ() - 0.5f);
        Vector3f rotatedOffset = new Quaternionf(facingRotation).transform(localOffset);

        Location displayLoc = base.clone().add(
                0.5 + rotatedOffset.x,
                rotatedOffset.y,
                0.5 + rotatedOffset.z);

        Quaternionf userRotation = new Quaternionf()
                .rotateX(rotX)
                .rotateY(rotY)
                .rotateZ(rotZ);

        Vector3f scale = new Vector3f(
                (float) plugin.cfg().placedBackpackRenderScaleX(),
                (float) plugin.cfg().placedBackpackRenderScaleY(),
                (float) plugin.cfg().placedBackpackRenderScaleZ());

        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setItemStack(renderItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(new Vector3f(0, 0, 0), facingRotation, scale, userRotation));
        });

        renderEntities.put(key, display.getUniqueId());
    }

    private void removeRender(String key, Location location) {
        UUID id = renderEntities.remove(key);
        if (id == null || location.getWorld() == null) {
            return;
        }

        Entity entity = location.getWorld().getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    /**
     * Handles non-player block destruction (e.g. flowing water/physics) by
     * dropping the actual backpack item before unregistering the placement.
     */
    private void dropAndRemoveBrokenPlacement(PlacedBackpack placed) {
        if (placed == null) {
            return;
        }

        Location location = placed.location();
        World world = location.getWorld();
        if (world != null) {
            ItemStack backpackItem = createRenderItem(placed);
            if (backpackItem != null && backpackItem.getType() != Material.AIR) {
                world.dropItemNaturally(location, backpackItem);
            }
        }

        remove(location);
    }

    private ItemStack createRenderItem(PlacedBackpack placed) {
        ItemStack item = backpackItems.createExisting(placed.backpackId(), placed.backpackType());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (!placed.modelDataStrings().isEmpty()) {
            CustomModelDataUtil.setCustomModelDataStrings(meta, placed.modelDataStrings());
        }

        if (!placed.modelDataColors().isEmpty()) {
            List<Color> colors = new ArrayList<>(placed.modelDataColors().size());
            for (Integer rgb : placed.modelDataColors()) {
                int value = rgb == null ? 0xFFFFFF : (rgb & 0xFFFFFF);
                colors.add(Color.fromRGB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF));
            }
            CustomModelDataUtil.setCustomModelDataColors(meta, colors);
        }

        item.setItemMeta(meta);
        return item;
    }

    private BlockFace getCurrentRotationFace(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.PLAYER_HEAD) {
            return BlockFace.NORTH;
        }
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Rotatable rotatable) {
            return rotatable.getRotation();
        }
        return BlockFace.NORTH;
    }

    private float baseFacingRadians(BlockFace face) {
        // Head rotation is a 16-step discrete facing. Use exact step values to avoid
        // slight off-angle drift on diagonal directions.
        float yawDegrees = switch (face) {
            case SOUTH -> 0.0f;
            case SOUTH_SOUTH_WEST -> 22.5f;
            case SOUTH_WEST -> 45.0f;
            case WEST_SOUTH_WEST -> 67.5f;
            case WEST -> 90.0f;
            case WEST_NORTH_WEST -> 112.5f;
            case NORTH_WEST -> 135.0f;
            case NORTH_NORTH_WEST -> 157.5f;
            case NORTH -> 180.0f;
            case NORTH_NORTH_EAST -> -157.5f;
            case NORTH_EAST -> -135.0f;
            case EAST_NORTH_EAST -> -112.5f;
            case EAST -> -90.0f;
            case EAST_SOUTH_EAST -> -67.5f;
            case SOUTH_EAST -> -45.0f;
            case SOUTH_SOUTH_EAST -> -22.5f;
            default -> {
                Vector direction = face.getDirection().clone();
                direction.setY(0);
                if (direction.lengthSquared() == 0) {
                    yield 0.0f;
                }
                direction.normalize();
                yield (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            }
        };

        // JOML Y-axis rotation sign is opposite of Bukkit yaw convention for our model
        // transform use here.
        return (float) Math.toRadians(-yawDegrees);
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
        List<String> modelDataStrings;
        List<Integer> modelDataColors;

        SavedPlacement(String backpackId, String backpackType, String worldName, int x, int y, int z,
                String ownerId, String ownerName, String rotation,
                List<String> modelDataStrings, List<Integer> modelDataColors) {
            this.backpackId = backpackId;
            this.backpackType = backpackType;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.rotation = rotation != null ? rotation : "NORTH";
            this.modelDataStrings = modelDataStrings == null ? new ArrayList<>() : new ArrayList<>(modelDataStrings);
            this.modelDataColors = modelDataColors == null ? new ArrayList<>() : new ArrayList<>(modelDataColors);
        }

        // Handle deserialization of old objects without rotation field
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (this.rotation == null) {
                this.rotation = "NORTH";
            }
            if (this.modelDataStrings == null) {
                this.modelDataStrings = new ArrayList<>();
            }
            if (this.modelDataColors == null) {
                this.modelDataColors = new ArrayList<>();
            }
        }
    }
}
