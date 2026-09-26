package io.github.tootertutor.ModularPacks.resource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Material;

/** Immutable configuration for one independently stackable resource pack. */
public record ResourcePackDefinition(
        String id,
        String displayName,
        String assetName,
        int priority,
        Material icon,
        UUID packId) {

    public ResourcePackDefinition {
        id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        assetName = assetName == null ? "" : assetName.trim();
        icon = icon == null ? Material.PAPER : icon;
        packId = packId == null ? stableId(id) : packId;
    }

    public static UUID stableId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(("modularpacks:resource-pack:" + normalized)
                .getBytes(StandardCharsets.UTF_8));
    }
}
