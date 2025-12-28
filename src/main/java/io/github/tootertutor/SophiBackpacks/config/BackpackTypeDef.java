package io.github.tootertutor.SophiBackpacks.config;

import org.bukkit.Material;

public record BackpackTypeDef(
		String id,
		String displayName,
		int rows,
		int upgradeSlots,
		Material outputMaterial) {
}
