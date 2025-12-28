package io.github.tootertutor.ModularPacks.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class ItemStackCodec {

    private ItemStackCodec() {
    }

    /*
     * ======================================================
     * Public API
     * ======================================================
     */

    public static byte[] toBytes(ItemStack[] contents) {
        String yaml = toYaml(contents);
        return gzip(yaml.getBytes(StandardCharsets.UTF_8));
    }

    public static ItemStack[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return new ItemStack[0];

        // GZIP header = 1F 8B
        boolean gz = bytes.length >= 2 && (bytes[0] == (byte) 0x1F) && (bytes[1] == (byte) 0x8B);
        if (!gz) {
            // Not a gzipped ItemStack payload (might be FurnaceStateCodec or something
            // else)
            return new ItemStack[0];
        }

        byte[] decompressed = gunzip(bytes);
        String yamlStr = new String(decompressed, StandardCharsets.UTF_8);
        return fromYaml(yamlStr);
    }

    public static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] fromBase64(String s) {
        return Base64.getDecoder().decode(s);
    }

    /*
     * ======================================================
     * YAML serialization
     * ======================================================
     */

    private static String toYaml(ItemStack[] contents) {
        YamlConfiguration yaml = new YamlConfiguration();

        // Fixed-size safeguard
        yaml.set("size", contents.length);

        List<Object> serialized = new ArrayList<>(contents.length);
        for (ItemStack item : contents) {
            serialized.add(item == null ? null : item.serialize());
        }
        yaml.set("items", serialized);

        try {
            return yaml.saveToString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ItemStack[] to YAML", e);
        }
    }

    private static ItemStack[] fromYaml(String yamlStr) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(yamlStr);
        } catch (InvalidConfigurationException e) {
            throw new RuntimeException("Failed to parse serialized ItemStack[] YAML", e);
        }

        int size = yaml.getInt("size", -1);
        List<?> saved = yaml.getList("items");

        if (saved == null) {
            return size >= 0 ? new ItemStack[size] : new ItemStack[0];
        }

        if (size < 0)
            size = saved.size();

        ItemStack[] items = new ItemStack[size];
        int limit = Math.min(saved.size(), size);

        for (int i = 0; i < limit; i++) {
            Object obj = saved.get(i);
            if (obj == null) {
                items[i] = null;
            } else if (obj instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                items[i] = ItemStack.deserialize(typed);
            } else {
                items[i] = null;
            }
        }

        return items;
    }

    /*
     * ======================================================
     * GZIP helpers
     * ======================================================
     */

    private static byte[] gzip(byte[] input) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(baos)) {

            gzip.write(input);
            gzip.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to GZIP-compress ItemStack data", e);
        }
    }

    private static byte[] gunzip(byte[] input) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(input);
                GZIPInputStream gzip = new GZIPInputStream(bais);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to GZIP-decompress ItemStack data", e);
        }
    }
}
