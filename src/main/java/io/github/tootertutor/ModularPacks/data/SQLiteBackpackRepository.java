package io.github.tootertutor.ModularPacks.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;

public final class SQLiteBackpackRepository {

    private final ModularPacksPlugin plugin;
    private Connection connection;

    public SQLiteBackpackRepository(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "backpacks.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            // Enable connection pooling optimizations for SQLite
            url += "?journal_mode=WAL&synchronous=NORMAL&cache_size=10000&temp_store=MEMORY";

            connection = DriverManager.getConnection(url);

            // Enable Write-Ahead Logging for better concurrency
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA synchronous=NORMAL;");
                st.execute("PRAGMA cache_size=10000;");
                st.execute("PRAGMA temp_store=MEMORY;");
                st.execute("PRAGMA busy_timeout=5000;"); // 5 second timeout for locked databases
            }

            try (Statement st = connection.createStatement()) {
                st.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS backpacks (
                              backpack_id TEXT PRIMARY KEY,
                              backpack_type TEXT NOT NULL,
                              contents BLOB,
                              owner_uuid TEXT,
                              owner_name TEXT,
                              created_at INTEGER,
                              updated_at INTEGER
                            );
                        """);
                // Schema migrations with logging
                migrateColumn(st, "backpacks", "owner_uuid", "TEXT");
                migrateColumn(st, "backpacks", "owner_name", "TEXT");
                migrateColumn(st, "backpacks", "created_at", "INTEGER");
                migrateColumn(st, "backpacks", "updated_at", "INTEGER");
                migrateColumn(st, "backpacks", "is_shared", "BOOLEAN DEFAULT 0");
                migrateColumn(st, "backpacks", "share_password", "TEXT");
                migrateColumn(st, "backpacks", "share_host_id", "TEXT");
                migrateColumn(st, "backpacks", "sort_locked", "BOOLEAN DEFAULT 0");

                st.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS backpack_modules (
                              backpack_id TEXT NOT NULL,
                              slot_index INTEGER NOT NULL,
                              module_id TEXT NOT NULL,
                              module_snapshot BLOB,
                              module_state BLOB,
                              PRIMARY KEY (backpack_id, slot_index)
                            );
                        """);
                migrateColumn(st, "backpack_modules", "module_state", "BLOB");

                // Void module audit + recovery log (full item bytes preserved)
                st.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS voided_items (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              created_at INTEGER NOT NULL,
                              player_uuid TEXT,
                              player_name TEXT,
                              backpack_id TEXT NOT NULL,
                              backpack_type TEXT,
                              void_module_id TEXT,
                              item_type TEXT,
                              amount INTEGER,
                              item_bytes BLOB NOT NULL,
                              world TEXT,
                              x REAL,
                              y REAL,
                              z REAL,
                              recovered_at INTEGER,
                              recovered_by TEXT,
                              recovered_by_name TEXT
                            );
                        """);
                st.executeUpdate("""
                            CREATE INDEX IF NOT EXISTS idx_voided_items_player_time
                            ON voided_items(player_uuid, created_at DESC);
                        """);
                st.executeUpdate("""
                            CREATE INDEX IF NOT EXISTS idx_voided_items_backpack_time
                            ON voided_items(backpack_id, created_at DESC);
                        """);
                st.executeUpdate("""
                            CREATE INDEX IF NOT EXISTS idx_voided_items_recovered
                            ON voided_items(recovered_at);
                        """);

            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init SQLite", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                // Checkpoint WAL file before closing
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to checkpoint WAL: " + e.getMessage());
                }
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
        }
    }

    /**
     * Get the current connection, reconnecting if necessary.
     * This helps recover from connection failures.
     */
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            plugin.getLogger().warning("Database connection lost, reconnecting...");
            init();
        }
        return connection;
    }

    public BackpackData loadOrCreate(UUID backpackId, String backpackType) {
        BackpackData data = new BackpackData(backpackId, backpackType);

        try {
            Connection conn = getConnection();
            // Load share metadata from the requesting backpackId
            UUID effectiveId = backpackId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT share_host_id, share_password, is_shared FROM backpacks WHERE backpack_id = ?")) {
                ps.setString(1, backpackId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String hostIdStr = rs.getString("share_host_id");
                        String password = rs.getString("share_password");
                        boolean isShared = rs.getBoolean("is_shared");

                        if (hostIdStr != null) {
                            // This is a joined backpack; load host's data
                            effectiveId = UUID.fromString(hostIdStr);
                            data.setShared(isShared);
                            data.sharePassword(password);
                            data.shareHostId(UUID.fromString(hostIdStr));
                        } else if (isShared) {
                            // This is a host backpack (shared but no host_id)
                            data.setShared(isShared);
                            data.sharePassword(password);
                        }
                    } else {
                        // Own backpack doesn't exist yet; ensure it will be created
                        // This is necessary to have the share columns initialized
                        try (PreparedStatement ins = getConnection().prepareStatement(
                                "INSERT INTO backpacks(backpack_id, backpack_type, contents, is_shared, share_password, share_host_id) VALUES(?,?,?,?,?,?)")) {
                            ins.setString(1, backpackId.toString());
                            ins.setString(2, backpackType);
                            ins.setBytes(3, null);
                            ins.setBoolean(4, false);
                            ins.setString(5, "");
                            ins.setString(6, null);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            // Load contents from effective backpack ID
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT backpack_type, contents, sort_locked FROM backpacks WHERE backpack_id = ?")) {
                ps.setString(1, effectiveId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.backpackType(rs.getString("backpack_type"));
                        data.contentsBytes(rs.getBytes("contents"));
                        data.sortLocked(rs.getBoolean("sort_locked"));
                    } else {
                        // insert new host backpack if it doesn't exist
                        try (PreparedStatement ins = getConnection().prepareStatement(
                                "INSERT INTO backpacks(backpack_id, backpack_type, contents, is_shared, share_password, share_host_id) VALUES(?,?,?,?,?,?)")) {
                            ins.setString(1, effectiveId.toString());
                            ins.setString(2, backpackType);
                            ins.setBytes(3, null);
                            ins.setBoolean(4, false);
                            ins.setString(5, "");
                            ins.setString(6, null);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            // modules from effective backpack ID
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT slot_index, module_id, module_snapshot, module_state FROM backpack_modules WHERE backpack_id = ?")) {
                ps.setString(1, effectiveId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int slotIndex = rs.getInt("slot_index");
                        UUID moduleId = UUID.fromString(rs.getString("module_id"));
                        byte[] snapshot = rs.getBytes("module_snapshot");
                        byte[] state = rs.getBytes("module_state");
                        if (state != null)
                            data.moduleStates().put(moduleId, state);

                        data.installedModules().put(slotIndex, moduleId);
                        if (snapshot != null)
                            data.installedSnapshots().put(moduleId, snapshot);
                    }
                }
            }

            return data;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load backpack " + backpackId, e);
        }
    }

    public String findBackpackType(UUID backpackId) {
        if (backpackId == null)
            return null;
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT backpack_type FROM backpacks WHERE backpack_id = ?")) {
            ps.setString(1, backpackId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("backpack_type");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query backpack type " + backpackId, e);
        }
        return null;
    }

    public boolean isPlayerValidShareMember(UUID playerId, UUID backpackId) {
        // Check if a player is authorized to access a shared backpack
        // This includes: being a participant in a shared host, or having joined as a
        // player
        if (playerId == null || backpackId == null)
            return false;

        try {
            // Load the backpack to check its share state
            BackpackData data = loadOrCreate(backpackId, null);
            if (data == null)
                return false;

            // If it's not shared, only the owner can access (not our concern here)
            if (!data.isShared())
                return false;

            // If loadOrCreate succeeded, this player has a row and is allowed
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public BackpackData loadJoinerContents(UUID joinerId) {
        // Load ONLY the joiner's original contents from their own row
        // Used when leaving a shared host to restore the joiner's items
        BackpackData data = new BackpackData(joinerId, null);

        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT backpack_type, contents FROM backpacks WHERE backpack_id = ?")) {
            ps.setString(1, joinerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString("backpack_type");
                    byte[] contents = rs.getBytes("contents");
                    data.backpackType(type);
                    data.contentsBytes(contents);
                    return data;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load joiner contents " + joinerId, e);
        }
        return null;
    }

    public UUID findBackpackByUuidPrefix(String uuidPrefix) {
        if (uuidPrefix == null || uuidPrefix.isEmpty())
            return null;
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT backpack_id FROM backpacks WHERE backpack_id LIKE ? LIMIT 1")) {
            ps.setString(1, uuidPrefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String idStr = rs.getString("backpack_id");
                    try {
                        return UUID.fromString(idStr);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query backpack by prefix " + uuidPrefix, e);
        }
        return null;
    }

    public void ensureBackpackExists(UUID backpackId, String backpackType, UUID ownerUuid, String ownerName) {
        if (backpackId == null || backpackType == null)
            return;

        long now = System.currentTimeMillis();

        try (PreparedStatement ins = getConnection().prepareStatement(
                """
                        INSERT OR IGNORE INTO backpacks(backpack_id, backpack_type, contents, owner_uuid, owner_name, created_at, updated_at)
                        VALUES(?,?,?,?,?,?,?)
                        """)) {
            ins.setString(1, backpackId.toString());
            ins.setString(2, backpackType);
            ins.setBytes(3, null);
            ins.setString(4, ownerUuid == null ? null : ownerUuid.toString());
            ins.setString(5, ownerName);
            ins.setLong(6, now);
            ins.setLong(7, now);
            ins.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure backpack exists " + backpackId, e);
        }

        try (PreparedStatement upd = getConnection().prepareStatement("""
                UPDATE backpacks
                   SET backpack_type = ?,
                       owner_uuid = COALESCE(?, owner_uuid),
                       owner_name = COALESCE(?, owner_name),
                       updated_at = ?
                 WHERE backpack_id = ?
                """)) {
            upd.setString(1, backpackType);
            upd.setString(2, ownerUuid == null ? null : ownerUuid.toString());
            upd.setString(3, ownerName);
            upd.setLong(4, now);
            upd.setString(5, backpackId.toString());
            upd.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update backpack metadata " + backpackId, e);
        }
    }

    public List<BackpackSummary> listBackpacksByOwner(UUID ownerUuid) {
        if (ownerUuid == null)
            return List.of();
        try (PreparedStatement ps = getConnection().prepareStatement("""
                SELECT backpack_id, backpack_type, owner_uuid, owner_name, created_at, updated_at
                  FROM backpacks
                 WHERE owner_uuid = ?
                 ORDER BY COALESCE(created_at, 0) ASC, backpack_id ASC
                """)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<BackpackSummary> out = new ArrayList<>();
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("backpack_id"));
                    String type = rs.getString("backpack_type");
                    String owner = rs.getString("owner_uuid");
                    String ownerName = rs.getString("owner_name");
                    long createdAt = rs.getLong("created_at");
                    long updatedAt = rs.getLong("updated_at");
                    out.add(new BackpackSummary(id, type, owner, ownerName, createdAt, updatedAt));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list backpacks for owner " + ownerUuid, e);
        }
    }

    public void saveBackpack(BackpackData data) {
        // If this is a joined backpack, we need to:
        // 1. Save contents to the HOST's backpack (so all joiners see changes)
        // 2. Save share metadata to the JOINER's backpack (keep metadata separate)
        // NOTE: Joiner's backup is saved separately in saveJoinerBackup() when joining

        if (data.shareHostId() != null) {
            // This is a joined backpack
            UUID hostId = data.shareHostId();
            UUID joinerId = data.backpackId();

            // Save contents to host (modifications visible to all joiners)
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE backpacks SET backpack_type = ?, contents = ?, sort_locked = ?, updated_at = ? WHERE backpack_id = ?")) {
                ps.setString(1, data.backpackType());
                ps.setBytes(2, data.contentsBytes());
                ps.setBoolean(3, data.sortLocked());
                ps.setLong(4, System.currentTimeMillis());
                ps.setString(5, hostId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save host backpack contents " + hostId, e);
            }

            // Save share metadata to joiner's backpack (don't overwrite host's metadata)
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE backpacks SET is_shared = ?, share_password = ?, share_host_id = ?, updated_at = ? WHERE backpack_id = ?")) {
                ps.setBoolean(1, data.isShared());
                ps.setString(2, data.sharePassword());
                ps.setString(3, data.shareHostId() != null ? data.shareHostId().toString() : null);
                ps.setLong(4, System.currentTimeMillis());
                ps.setString(5, joinerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save joiner metadata " + joinerId, e);
            }

            // Modules go to the host's backpack (shared state)
            saveModules(hostId, data.installedModules(), data.installedSnapshots(), data.moduleStates());
        } else {
            // This is an own backpack (not joined)
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE backpacks SET backpack_type = ?, contents = ?, sort_locked = ?, updated_at = ?, is_shared = ?, share_password = ?, share_host_id = ? WHERE backpack_id = ?")) {
                ps.setString(1, data.backpackType());
                ps.setBytes(2, data.contentsBytes());
                ps.setBoolean(3, data.sortLocked());
                ps.setLong(4, System.currentTimeMillis());
                ps.setBoolean(5, data.isShared());
                ps.setString(6, data.sharePassword());
                ps.setString(7, data.shareHostId() != null ? data.shareHostId().toString() : null);
                ps.setString(8, data.backpackId().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save backpack " + data.backpackId(), e);
            }

            saveModules(data.backpackId(), data.installedModules(), data.installedSnapshots(), data.moduleStates());
        }
    }

    public void saveShareMetadataOnly(BackpackData data) {
        // Save ONLY the share metadata without touching contents
        // Used when joining/leaving to avoid overwriting backpack contents
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE backpacks SET is_shared = ?, share_password = ?, share_host_id = ?, updated_at = ? WHERE backpack_id = ?")) {
            ps.setBoolean(1, data.isShared());
            ps.setString(2, data.sharePassword());
            ps.setString(3, data.shareHostId() != null ? data.shareHostId().toString() : null);
            ps.setLong(4, System.currentTimeMillis());
            ps.setString(5, data.backpackId().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save share metadata for " + data.backpackId(), e);
        }
    }

    /**
     * Save a joiner's backup contents when they first join a host.
     * This preserves their original contents so they can be restored when leaving.
     */
    public void saveJoinerBackup(UUID joinerId, BackpackData data) {
        // Save ONLY the contents to the joiner's row (metadata will be updated
        // separately)
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE backpacks SET contents = ?, updated_at = ? WHERE backpack_id = ?")) {
            ps.setBytes(1, data.contentsBytes());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, joinerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save joiner backup for " + joinerId, e);
        }
    }

    /**
     * Disconnect all backpacks that have joined the specified host.
     * Sets their is_shared=false, share_host_id=null, share_password=''.
     * Called when a host backpack goes back to private mode.
     */
    public java.util.List<UUID> disconnectAllJoinedBackpacks(UUID hostId) {
        if (hostId == null)
            return java.util.List.of();

        java.util.List<UUID> joinedIds = new java.util.ArrayList<>();

        // Collect joined backpack IDs first (so we can close their sessions)
        try (PreparedStatement find = connection
                .prepareStatement("SELECT backpack_id FROM backpacks WHERE share_host_id = ?")) {
            find.setString(1, hostId.toString());
            try (ResultSet rs = find.executeQuery()) {
                while (rs.next()) {
                    String idStr = rs.getString("backpack_id");
                    try {
                        joinedIds.add(UUID.fromString(idStr));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list joined backpacks for host " + hostId, e);
        }

        // Flip them back to private
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE backpacks SET is_shared = 0, share_host_id = NULL, share_password = '', updated_at = ? WHERE share_host_id = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, hostId.toString());
            int updated = ps.executeUpdate();
            if (updated > 0) {
                plugin.getLogger().info(
                        "[ModularPacks] Disconnected " + updated + " joined backpack(s) from host " + hostId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to disconnect joined backpacks from host " + hostId, e);
        }

        return joinedIds;
    }

    /**
     * List backpack IDs that are currently joined to the given host.
     */
    public java.util.List<UUID> listJoinedBackpacks(UUID hostId) {
        if (hostId == null)
            return java.util.List.of();

        java.util.List<UUID> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = connection
                .prepareStatement("SELECT backpack_id FROM backpacks WHERE share_host_id = ?")) {
            ps.setString(1, hostId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idStr = rs.getString("backpack_id");
                    try {
                        out.add(UUID.fromString(idStr));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list joined backpacks for host " + hostId, e);
        }
        return out;
    }

    /**
     * Helper method for schema migrations with logging.
     */
    private void migrateColumn(Statement st, String table, String column, String definition) {
        try {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            plugin.getLogger().info("Added column " + column + " to table " + table);
        } catch (SQLException e) {
            // Column already exists, ignore
        }
    }

    public void saveModules(UUID backpackId, Map<Integer, UUID> slotToModule, Map<UUID, byte[]> snapshots,
            Map<UUID, byte[]> states) {
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement del = getConnection().prepareStatement(
                    "DELETE FROM backpack_modules WHERE backpack_id = ?")) {
                del.setString(1, backpackId.toString());
                del.executeUpdate();
            }

            try (PreparedStatement ins = getConnection().prepareStatement(
                    "INSERT INTO backpack_modules(backpack_id, slot_index, module_id, module_snapshot, module_state) VALUES(?,?,?,?,?)")) {
                for (Map.Entry<Integer, UUID> e : slotToModule.entrySet()) {
                    UUID moduleId = e.getValue();

                    ins.setString(1, backpackId.toString());
                    ins.setInt(2, e.getKey());
                    ins.setString(3, moduleId.toString());
                    ins.setBytes(4, snapshots.get(moduleId));
                    ins.setBytes(5, states.get(moduleId));
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            throw new RuntimeException("Failed to save modules for " + backpackId, e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public List<BackpackSummary> listUnownedBackpacks(int limit) {
        limit = Math.max(1, Math.min(500, limit));
        try (PreparedStatement ps = getConnection().prepareStatement("""
                SELECT backpack_id, backpack_type, owner_uuid, owner_name, created_at, updated_at
                  FROM backpacks
                 WHERE owner_uuid IS NULL
                 ORDER BY COALESCE(created_at, 0) ASC, backpack_id ASC
                 LIMIT ?
                """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<BackpackSummary> out = new ArrayList<>();
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("backpack_id"));
                    String type = rs.getString("backpack_type");
                    String owner = rs.getString("owner_uuid");
                    String ownerName = rs.getString("owner_name");
                    long createdAt = rs.getLong("created_at");
                    long updatedAt = rs.getLong("updated_at");
                    out.add(new BackpackSummary(id, type, owner, ownerName, createdAt, updatedAt));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list unowned backpacks", e);
        }
    }

    public record BackpackSummary(
            UUID backpackId,
            String backpackType,
            String ownerUuid,
            String ownerName,
            long createdAt,
            long updatedAt) {
    }

    public long logVoidedItem(VoidedItemRecord rec) {
        if (rec == null || rec.itemBytes == null)
            return -1;

        try (PreparedStatement ps = getConnection().prepareStatement("""
                INSERT INTO voided_items(
                    created_at,
                    player_uuid,
                    player_name,
                    backpack_id,
                    backpack_type,
                    void_module_id,
                    item_type,
                    amount,
                    item_bytes,
                    world,
                    x,
                    y,
                    z,
                    recovered_at,
                    recovered_by,
                    recovered_by_name
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, rec.createdAt);
            ps.setString(2, rec.playerUuid);
            ps.setString(3, rec.playerName);
            ps.setString(4, rec.backpackId);
            ps.setString(5, rec.backpackType);
            ps.setString(6, rec.voidModuleId);
            ps.setString(7, rec.itemType);
            ps.setInt(8, rec.amount);
            ps.setBytes(9, rec.itemBytes);
            ps.setString(10, rec.world);
            ps.setObject(11, rec.x);
            ps.setObject(12, rec.y);
            ps.setObject(13, rec.z);
            ps.setObject(14, rec.recoveredAt);
            ps.setString(15, rec.recoveredBy);
            ps.setString(16, rec.recoveredByName);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log voided item", e);
        }
    }

    public List<VoidedItemSummary> listVoidedItemsByPlayer(UUID playerUuid, int limit, boolean includeRecovered) {
        if (playerUuid == null)
            return List.of();
        limit = Math.max(1, Math.min(200, limit));

        String sql = includeRecovered
                ? """
                        SELECT id, created_at, player_uuid, player_name, backpack_id, backpack_type, void_module_id,
                               item_type, amount, world, x, y, z, recovered_at, recovered_by, recovered_by_name
                          FROM voided_items
                         WHERE player_uuid = ?
                         ORDER BY created_at DESC, id DESC
                         LIMIT ?
                        """
                : """
                        SELECT id, created_at, player_uuid, player_name, backpack_id, backpack_type, void_module_id,
                               item_type, amount, world, x, y, z, recovered_at, recovered_by, recovered_by_name
                          FROM voided_items
                         WHERE player_uuid = ? AND recovered_at IS NULL
                         ORDER BY created_at DESC, id DESC
                         LIMIT ?
                        """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<VoidedItemSummary> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(VoidedItemSummary.from(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list voided items for " + playerUuid, e);
        }
    }

    public VoidedItemRecord getVoidedItem(long id) {
        if (id <= 0)
            return null;
        try (PreparedStatement ps = getConnection().prepareStatement("""
                SELECT id, created_at, player_uuid, player_name, backpack_id, backpack_type, void_module_id,
                       item_type, amount, item_bytes, world, x, y, z, recovered_at, recovered_by, recovered_by_name
                  FROM voided_items
                 WHERE id = ?
                """)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return null;
                return VoidedItemRecord.from(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get voided item " + id, e);
        }
    }

    public boolean markVoidedItemRecovered(long id, UUID recoveredBy, String recoveredByName) {
        if (id <= 0)
            return false;
        long now = System.currentTimeMillis();

        try (PreparedStatement ps = getConnection().prepareStatement("""
                UPDATE voided_items
                   SET recovered_at = ?,
                       recovered_by = ?,
                       recovered_by_name = ?
                 WHERE id = ? AND recovered_at IS NULL
                """)) {
            ps.setLong(1, now);
            ps.setString(2, recoveredBy == null ? null : recoveredBy.toString());
            ps.setString(3, recoveredByName);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark voided item recovered " + id, e);
        }
    }

    public static final class VoidedItemSummary {
        public final long id;
        public final long createdAt;
        public final String playerUuid;
        public final String playerName;
        public final String backpackId;
        public final String backpackType;
        public final String voidModuleId;
        public final String itemType;
        public final int amount;
        public final String world;
        public final Double x;
        public final Double y;
        public final Double z;
        public final Long recoveredAt;
        public final String recoveredBy;
        public final String recoveredByName;

        private VoidedItemSummary(
                long id,
                long createdAt,
                String playerUuid,
                String playerName,
                String backpackId,
                String backpackType,
                String voidModuleId,
                String itemType,
                int amount,
                String world,
                Double x,
                Double y,
                Double z,
                Long recoveredAt,
                String recoveredBy,
                String recoveredByName) {
            this.id = id;
            this.createdAt = createdAt;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.backpackId = backpackId;
            this.backpackType = backpackType;
            this.voidModuleId = voidModuleId;
            this.itemType = itemType;
            this.amount = amount;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.recoveredAt = recoveredAt;
            this.recoveredBy = recoveredBy;
            this.recoveredByName = recoveredByName;
        }

        private static VoidedItemSummary from(ResultSet rs) throws SQLException {
            return new VoidedItemSummary(
                    rs.getLong("id"),
                    rs.getLong("created_at"),
                    rs.getString("player_uuid"),
                    rs.getString("player_name"),
                    rs.getString("backpack_id"),
                    rs.getString("backpack_type"),
                    rs.getString("void_module_id"),
                    rs.getString("item_type"),
                    rs.getInt("amount"),
                    rs.getString("world"),
                    (Double) rs.getObject("x"),
                    (Double) rs.getObject("y"),
                    (Double) rs.getObject("z"),
                    (Long) rs.getObject("recovered_at"),
                    rs.getString("recovered_by"),
                    rs.getString("recovered_by_name"));
        }
    }

    public static final class VoidedItemRecord {
        public final Long id;
        public final long createdAt;
        public final String playerUuid;
        public final String playerName;
        public final String backpackId;
        public final String backpackType;
        public final String voidModuleId;
        public final String itemType;
        public final int amount;
        public final byte[] itemBytes;
        public final String world;
        public final Double x;
        public final Double y;
        public final Double z;
        public final Long recoveredAt;
        public final String recoveredBy;
        public final String recoveredByName;

        public VoidedItemRecord(
                Long id,
                long createdAt,
                String playerUuid,
                String playerName,
                String backpackId,
                String backpackType,
                String voidModuleId,
                String itemType,
                int amount,
                byte[] itemBytes,
                String world,
                Double x,
                Double y,
                Double z,
                Long recoveredAt,
                String recoveredBy,
                String recoveredByName) {
            this.id = id;
            this.createdAt = createdAt;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.backpackId = backpackId;
            this.backpackType = backpackType;
            this.voidModuleId = voidModuleId;
            this.itemType = itemType;
            this.amount = amount;
            this.itemBytes = itemBytes;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.recoveredAt = recoveredAt;
            this.recoveredBy = recoveredBy;
            this.recoveredByName = recoveredByName;
        }

        private static VoidedItemRecord from(ResultSet rs) throws SQLException {
            return new VoidedItemRecord(
                    rs.getLong("id"),
                    rs.getLong("created_at"),
                    rs.getString("player_uuid"),
                    rs.getString("player_name"),
                    rs.getString("backpack_id"),
                    rs.getString("backpack_type"),
                    rs.getString("void_module_id"),
                    rs.getString("item_type"),
                    rs.getInt("amount"),
                    rs.getBytes("item_bytes"),
                    rs.getString("world"),
                    (Double) rs.getObject("x"),
                    (Double) rs.getObject("y"),
                    (Double) rs.getObject("z"),
                    (Long) rs.getObject("recovered_at"),
                    rs.getString("recovered_by"),
                    rs.getString("recovered_by_name"));
        }
    }
}
