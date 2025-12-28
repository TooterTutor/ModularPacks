package io.github.tootertutor.ModularPacks.modules;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TankStateCodec {

    private TankStateCodec() {
    }

    public static final class State {
        /** When true, the tank is in EXP mode (only valid when empty). */
        public boolean expMode;

        /** Stored fluid type as a bucket material name (e.g. WATER_BUCKET). */
        public String fluidBucketMaterial;

        /** Number of buckets stored for current fluid type (0..capacity). */
        public int fluidBuckets;

        /** Stored experience, in whole player levels (0..max). */
        public int expLevels;
    }

    public static byte[] encode(State s) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("v", 2);
        yaml.set("expMode", s.expMode);
        yaml.set("fluidBucketMaterial", s.fluidBucketMaterial);
        yaml.set("fluidBuckets", Math.max(0, s.fluidBuckets));
        yaml.set("expLevels", Math.max(0, s.expLevels));
        return yaml.saveToString().getBytes(StandardCharsets.UTF_8);
    }

    public static State decode(byte[] bytes) {
        State s = new State();
        if (bytes == null || bytes.length == 0)
            return s;

        String str = new String(bytes, StandardCharsets.UTF_8);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(str);
        } catch (InvalidConfigurationException e) {
            return s;
        }

        // v2 fields
        s.expMode = yaml.getBoolean("expMode", false);
        s.fluidBucketMaterial = yaml.getString("fluidBucketMaterial");
        s.fluidBuckets = Math.max(0, yaml.getInt("fluidBuckets", 0));
        s.expLevels = Math.max(0, yaml.getInt("expLevels", 0));

        // v1 compatibility (waterBuckets/lavaBuckets)
        int water = Math.max(0, yaml.getInt("waterBuckets", 0));
        int lava = Math.max(0, yaml.getInt("lavaBuckets", 0));
        if (s.fluidBuckets <= 0 && water > 0) {
            s.fluidBucketMaterial = "WATER_BUCKET";
            s.fluidBuckets = water;
        } else if (s.fluidBuckets <= 0 && lava > 0) {
            s.fluidBucketMaterial = "LAVA_BUCKET";
            s.fluidBuckets = lava;
        }

        // sanitize impossible mixed states
        if (s.expLevels > 0) {
            s.expMode = true;
            s.fluidBuckets = 0;
            s.fluidBucketMaterial = null;
        }
        if (s.fluidBuckets > 0) {
            s.expLevels = 0;
            s.expMode = false;
        }

        if (s.fluidBucketMaterial != null && s.fluidBucketMaterial.isBlank()) {
            s.fluidBucketMaterial = null;
        }
        if (s.fluidBucketMaterial != null) {
            s.fluidBucketMaterial = s.fluidBucketMaterial.trim().toUpperCase(Locale.ROOT);
        }
        return s;
    }
}
