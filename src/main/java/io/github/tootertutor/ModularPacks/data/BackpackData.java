package io.github.tootertutor.ModularPacks.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Backpack persistent state (per backpack UUID).
 * - contents: full logical storage (rows*9), NOT just current page
 * - installedModules: slotIndex (0..upgradeSlots-1) -> moduleId
 * - installedSnapshots: moduleId -> serialized ItemStack snapshot (fallback
 * safety)
 */
public final class BackpackData {

    private final UUID backpackId;
    private String backpackType;

    private byte[] contentsBytes; // ItemStack[] bytes

    private final Map<Integer, UUID> installedModules = new HashMap<>();
    private final Map<UUID, byte[]> installedSnapshots = new HashMap<>();
    private final Map<UUID, byte[]> moduleStates = new HashMap<>();

    public BackpackData(UUID backpackId, String backpackType) {
        this.backpackId = backpackId;
        this.backpackType = backpackType;
    }

    public UUID backpackId() {
        return backpackId;
    }

    public String backpackType() {
        return backpackType;
    }

    public void backpackType(String type) {
        this.backpackType = type;
    }

    public byte[] contentsBytes() {
        return contentsBytes;
    }

    public void contentsBytes(byte[] bytes) {
        this.contentsBytes = bytes;
    }

    public Map<Integer, UUID> installedModules() {
        return installedModules;
    }

    public Map<UUID, byte[]> installedSnapshots() {
        return installedSnapshots;
    }

    public Map<UUID, byte[]> moduleStates() {
        return moduleStates;
    }

}
