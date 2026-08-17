package io.github.tootertutor.ModularPacks.modules.quiver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

/**
 * Versioned configuration for one Quiver instance. The sole ItemStack is an
 * amount-one identity selector, never ammunition storage.
 */
public final class QuiverSelectionCodec {

    private static final int MAGIC = 0x4D505156; // MPQV
    private static final int VERSION = 1;
    private static final int MAX_ITEM_BYTES = 16 * 1024 * 1024;

    public byte[] encode(QuiverSelection selection) {
        QuiverSelection value = selection == null ? QuiverSelection.auto() : selection;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(value.mode().ordinal());
            ItemStack prototype = value.selectedPrototype();
            if (prototype == null) {
                out.writeInt(0);
            } else {
                byte[] itemBytes = ItemStackCodec.toBytes(new ItemStack[] { prototype });
                out.writeInt(itemBytes.length);
                out.write(itemBytes);
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode Quiver selection", ex);
        }
    }

    public QuiverSelection decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return QuiverSelection.auto();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) {
                return QuiverSelection.auto();
            }
            int modeOrdinal = in.readUnsignedByte();
            int itemLength = in.readInt();
            if (itemLength < 0 || itemLength > MAX_ITEM_BYTES || itemLength > in.available()) {
                return QuiverSelection.auto();
            }
            if (modeOrdinal != QuiverSelectionMode.EXACT.ordinal() || itemLength == 0) {
                return QuiverSelection.auto();
            }
            byte[] itemBytes = in.readNBytes(itemLength);
            ItemStack[] decoded = ItemStackCodec.fromBytes(itemBytes);
            // Reject an obsolete or malformed inventory-like payload rather than
            // interpreting any extra entries as ammunition owned by the module.
            if (decoded.length != 1 || !QuiverAmmoService.isSupported(decoded[0])) {
                return QuiverSelection.auto();
            }
            return QuiverSelection.exact(decoded[0]);
        } catch (IOException | RuntimeException ex) {
            return QuiverSelection.auto();
        }
    }
}
