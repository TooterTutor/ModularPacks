package io.github.tootertutor.ModularPacks.modules.crafting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

public final class AutocraftingStateCodec {

    private static final int MAGIC = 0x4d504143; // "MPAC"
    private static final int VERSION = 1;

    private AutocraftingStateCodec() {
    }

    public record State(ItemStack[] inventoryItems, int desiredAmount, int cooldownTicks) {
    }

    public static byte[] encode(State state) {
        if (state == null) {
            return ItemStackCodec.toBytes(new ItemStack[0]);
        }

        ItemStack[] items = state.inventoryItems() == null ? new ItemStack[0] : state.inventoryItems();
        byte[] itemBytes = ItemStackCodec.toBytes(items);
        int desired = clampDesiredAmount(state.desiredAmount());
        int cooldown = Math.max(0, state.cooldownTicks());

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(desired);
            out.writeInt(cooldown);
            out.writeInt(itemBytes.length);
            out.write(itemBytes);
            out.flush();
            return baos.toByteArray();
        } catch (Exception ex) {
            return ItemStackCodec.toBytes(items);
        }
    }

    public static State decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new State(new ItemStack[0], 1, 0);
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("Not autocrafting state bytes");
            }

            int version = in.readInt();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported autocrafting state version: " + version);
            }

            int desired = clampDesiredAmount(in.readInt());
            int cooldown = Math.max(0, in.readInt());
            int len = in.readInt();
            if (len < 0 || len > (16 * 1024 * 1024)) {
                throw new IllegalArgumentException("Invalid item payload size: " + len);
            }

            byte[] itemBytes = new byte[len];
            in.readFully(itemBytes);
            ItemStack[] items = ItemStackCodec.fromBytes(itemBytes);
            return new State(items, desired, cooldown);
        } catch (Exception ex) {
            ItemStack[] legacy = ItemStackCodec.fromBytes(bytes);
            return new State(legacy, 1, 0);
        }
    }

    public static int clampDesiredAmount(int desired) {
        return Math.max(1, Math.min(64, desired));
    }
}
