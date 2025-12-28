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
            connection = DriverManager.getConnection(url);

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
                try {
                    st.executeUpdate("ALTER TABLE backpacks ADD COLUMN owner_uuid TEXT");
                } catch (SQLException ignored) {
                }
                try {
                    st.executeUpdate("ALTER TABLE backpacks ADD COLUMN owner_name TEXT");
                } catch (SQLException ignored) {
                }
                try {
                    st.executeUpdate("ALTER TABLE backpacks ADD COLUMN created_at INTEGER");
                } catch (SQLException ignored) {
                }
                try {
                    st.executeUpdate("ALTER TABLE backpacks ADD COLUMN updated_at INTEGER");
                } catch (SQLException ignored) {
                }

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
                try {
                    st.executeUpdate("ALTER TABLE backpack_modules ADD COLUMN module_state BLOB");
                } catch (SQLException ignored) {
                    // already exists
                }

            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init SQLite", e);
        }
    }

    public void close() {
        try {
            if (connection != null)
                connection.close();
        } catch (SQLException ignored) {
        }
    }

    public BackpackData loadOrCreate(UUID backpackId, String backpackType) {
        BackpackData data = new BackpackData(backpackId, backpackType);

        try {
            // backpacks row
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT backpack_type, contents FROM backpacks WHERE backpack_id = ?")) {
                ps.setString(1, backpackId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.backpackType(rs.getString("backpack_type"));
                        data.contentsBytes(rs.getBytes("contents"));
                    } else {
                        // insert new
                        try (PreparedStatement ins = connection.prepareStatement(
                                "INSERT INTO backpacks(backpack_id, backpack_type, contents) VALUES(?,?,?)")) {
                            ins.setString(1, backpackId.toString());
                            ins.setString(2, backpackType);
                            ins.setBytes(3, null);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            // modules
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT slot_index, module_id, module_snapshot, module_state FROM backpack_modules WHERE backpack_id = ?")) {
                ps.setString(1, backpackId.toString());
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
        try (PreparedStatement ps = connection.prepareStatement(
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

    public void ensureBackpackExists(UUID backpackId, String backpackType, UUID ownerUuid, String ownerName) {
        if (backpackId == null || backpackType == null)
            return;

        long now = System.currentTimeMillis();

        try (PreparedStatement ins = connection.prepareStatement(

                """
                                        INSERT OR IGN
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

        try (PreparedStatement upd = connection.prepareStatement("""
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
        try (PreparedStatement ps = connection.prepareStatement("""
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
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE backpacks SET backpack_type = ?, contents = ?, updated_at = ? WHERE backpack_id = ?")) {
            ps.setString(1, data.backpackType());
            ps.setBytes(2, data.contentsBytes());
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, data.backpackId().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save backpack " + data.backpackId(), e);
        }

        saveModules(data.backpackId(), data.installedModules(), data.installedSnapshots(), data.moduleStates());

    }

    public void saveModules(UUID backpackId, Map<Integer, UUID> slotToModule, Map<UUID, byte[]> snapshots,
            Map<UUID, byte[]> states) {
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM backpack_modules WHERE backpack_id = ?")) {
                del.setString(1, backpackId.toString());
                del.executeUpdate();
            }

            try (PreparedStatement ins = connection.prepareStatement(
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
        try (PreparedStatement ps = connection.prepareStatement("""
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
}
