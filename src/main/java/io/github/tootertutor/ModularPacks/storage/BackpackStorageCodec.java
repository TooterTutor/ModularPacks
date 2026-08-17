package io.github.tootertutor.ModularPacks.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Versioned codec dedicated to logical backpack contents.
 */
public final class BackpackStorageCodec {

    public static final int CURRENT_VERSION = 2;

    private static final byte[] MAGIC = { 'M', 'P', 'B', 'S' };
    private static final int HEADER_SIZE = MAGIC.length + Integer.BYTES;

    public boolean isEncodedStorage(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER_SIZE) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public byte[] encode(BackpackStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("storage cannot be null");
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", CURRENT_VERSION);
        yaml.set("size", storage.size());

        List<Object> serializedSlots = new ArrayList<>(storage.size());
        for (int i = 0; i < storage.size(); i++) {
            StoredStack stored = storage.get(i);
            if (stored == null) {
                serializedSlots.add(null);
                continue;
            }

            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("prototype", stored.prototype().serialize());
            serialized.put("count", stored.count());
            serializedSlots.add(serialized);
        }
        yaml.set("slots", serializedSlots);

        try {
            byte[] payload = gzip(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_SIZE + payload.length);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.write(MAGIC);
                out.writeInt(CURRENT_VERSION);
                out.write(payload);
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to serialize backpack storage", ex);
        }
    }

    public BackpackStorage decode(byte[] bytes) {
        if (!isEncodedStorage(bytes)) {
            throw new IllegalArgumentException("Payload is not versioned backpack storage");
        }

        int version;
        byte[] compressed;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(MAGIC, magic)) {
                throw new IllegalArgumentException("Invalid backpack storage magic header");
            }
            version = in.readInt();
            compressed = in.readAllBytes();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read backpack storage header", ex);
        }

        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported backpack storage version: " + version);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            String serialized = new String(gunzip(compressed), StandardCharsets.UTF_8);
            yaml.loadFromString(serialized);
        } catch (InvalidConfigurationException ex) {
            throw new RuntimeException("Failed to parse backpack storage YAML", ex);
        }

        int payloadVersion = yaml.getInt("version", -1);
        if (payloadVersion != version) {
            throw new IllegalArgumentException(
                    "Backpack storage payload version " + payloadVersion + " does not match header " + version);
        }

        int size = yaml.getInt("size", -1);
        if (size < 0) {
            throw new IllegalArgumentException("Backpack storage size is missing or negative");
        }

        BackpackStorage storage = new BackpackStorage(size);
        List<?> serializedSlots = yaml.getList("slots");
        if (serializedSlots == null) {
            return storage;
        }

        int limit = Math.min(size, serializedSlots.size());
        for (int i = 0; i < limit; i++) {
            Object rawSlot = serializedSlots.get(i);
            if (rawSlot == null) {
                continue;
            }
            if (!(rawSlot instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("Invalid backpack storage entry at slot " + i);
            }

            Object rawPrototype = rawMap.get("prototype");
            ItemStack prototype = deserializePrototype(rawPrototype, i);
            long count = deserializeCount(rawMap.get("count"), i);
            storage.set(i, new StoredStack(prototype, count));
        }
        return storage;
    }

    private static ItemStack deserializePrototype(Object rawPrototype, int slot) {
        if (rawPrototype instanceof ItemStack item) {
            return item.clone();
        }
        if (rawPrototype instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> serialized = (Map<String, Object>) rawMap;
            return ItemStack.deserialize(serialized);
        }
        throw new IllegalArgumentException("Missing or invalid prototype at slot " + slot);
    }

    private static long deserializeCount(Object rawCount, int slot) {
        if (!(rawCount instanceof Byte || rawCount instanceof Short
                || rawCount instanceof Integer || rawCount instanceof Long)) {
            throw new IllegalArgumentException("Missing or invalid count at slot " + slot);
        }
        long count = ((Number) rawCount).longValue();
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than zero at slot " + slot);
        }
        return count;
    }

    private static byte[] gzip(byte[] input) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(input);
            gzip.finish();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to compress backpack storage", ex);
        }
    }

    private static byte[] gunzip(byte[] input) {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(input);
                GZIPInputStream gzip = new GZIPInputStream(bytes);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to decompress backpack storage", ex);
        }
    }
}
