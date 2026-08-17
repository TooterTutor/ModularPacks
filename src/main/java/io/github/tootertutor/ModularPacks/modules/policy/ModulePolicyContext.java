package io.github.tootertutor.ModularPacks.modules.policy;

import java.util.List;
import java.util.UUID;

import io.github.tootertutor.ModularPacks.config.UpgradeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;

/**
 * Immutable view of a requested module mutation and the backpack's installed
 * modules.
 */
public record ModulePolicyContext(
        BackpackData backpackData,
        String moduleType,
        UUID moduleId,
        UpgradeDef moduleDefinition,
        List<InstalledModule> installedModules) {

    public ModulePolicyContext {
        installedModules = installedModules == null ? List.of() : List.copyOf(installedModules);
    }

    public record InstalledModule(int socketIndex, UUID moduleId, String moduleType) {
    }
}
