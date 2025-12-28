package io.github.tootertutor.ModularPacks.data;

public interface ModuleStateCodec {
    String id(); // e.g. "Tank", "Smelting"

    byte[] encode(Object state);

    Object decode(byte[] bytes);
}
