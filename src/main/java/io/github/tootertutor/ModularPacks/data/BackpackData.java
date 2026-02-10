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

    // Sharing fields
    private boolean isShared = false;
    private String sharePassword = "";
    private UUID shareHostId = null; // null if this is the host, otherwise UUID of the host backpack

    // Sort fields
    private boolean sortLocked = false;

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

    public boolean isShared() {
        return isShared;
    }

    public void setShared(boolean shared) {
        this.isShared = shared;
    }

    public String sharePassword() {
        return sharePassword;
    }

    public void sharePassword(String password) {
        this.sharePassword = password == null ? "" : password;
    }

    public UUID shareHostId() {
        return shareHostId;
    }

    public void shareHostId(UUID hostId) {
        this.shareHostId = hostId;
    }

    public boolean sortLocked() {
        return sortLocked;
    }

    public void sortLocked(boolean locked) {
        this.sortLocked = locked;
    }

    /**
     * Get the effective backpack ID to load data from.
     * If this backpack is joined to a host, return the host ID; otherwise return
     * this backpack's ID.
     */
    public UUID getEffectiveBackpackId() {
        return shareHostId != null ? shareHostId : backpackId;
    }

    /**
     * Check if this backpack is a host (shared but has no host ID).
     */
    public boolean isShareHost() {
        return isShared && shareHostId == null;
    }

}
