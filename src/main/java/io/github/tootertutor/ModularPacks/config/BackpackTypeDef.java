package io.github.tootertutor.ModularPacks.config;

import org.bukkit.Material;

public record BackpackTypeDef(
		String id,
		String displayName,
		int rows,
		int upgradeSlots,
		Material outputMaterial) {
}
