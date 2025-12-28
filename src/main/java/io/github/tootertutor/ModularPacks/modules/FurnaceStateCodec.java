package io.github.tootertutor.ModularPacks.modules;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class FurnaceStateCodec {

    private FurnaceStateCodec() {
    }

    public static final class State {
        public ItemStack input;
        public ItemStack fuel;
        public ItemStack output;

        public int burnTime;
        public int burnTotal;
        public int cookTime;
        public int cookTotal;
    }

    public static byte[] encode(State s) {
        YamlConfiguration yaml = new YamlConfiguration();

        List<Object> items = new ArrayList<>(3);
        items.add(s.input == null ? null : s.input.serialize());
        items.add(s.fuel == null ? null : s.fuel.serialize());
        items.add(s.output == null ? null : s.output.serialize());
        yaml.set("items", items);

        yaml.set("burnTime", s.burnTime);
        yaml.set("burnTotal", s.burnTotal);
        yaml.set("cookTime", s.cookTime);
        yaml.set("cookTotal", s.cookTotal);

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
            // If the old format was raw ItemStack[] bytes, just return empty state.
            return s;
        }

        List<?> items = yaml.getList("items");
        if (items != null) {
            s.input = deserializeItem(items, 0);
            s.fuel = deserializeItem(items, 1);
            s.output = deserializeItem(items, 2);
        }

        s.burnTime = yaml.getInt("burnTime", 0);
        s.burnTotal = yaml.getInt("burnTotal", 0);
        s.cookTime = yaml.getInt("cookTime", 0);
        s.cookTotal = yaml.getInt("cookTotal", 0);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack deserializeItem(List<?> items, int idx) {
        if (idx < 0 || idx >= items.size())
            return null;
        Object o = items.get(idx);
        if (!(o instanceof Map<?, ?> m))
            return null;
        return ItemStack.deserialize((Map<String, Object>) m);
    }
}
