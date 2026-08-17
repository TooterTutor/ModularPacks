package io.github.tootertutor.ModularPacks.modules.policy;

import io.github.tootertutor.ModularPacks.modules.tank.TankModuleLogic;

/**
 * Preserves the legacy mixed-Tank incompatibility with split tank modules.
 * Exact-type counts are intentionally left to InstallationLimitPolicy.
 */
final class TankCompatibilityPolicy implements ModulePolicy {

    @Override
    public ModulePolicyDecision canInstall(ModulePolicyContext context) {
        String requestedType = context.moduleType();
        for (ModulePolicyContext.InstalledModule installed : context.installedModules()) {
            String installedType = installed.moduleType();
            if (requestedType == null || installedType == null || requestedType.equalsIgnoreCase(installedType)) {
                continue;
            }
            if (TankModuleLogic.tankTypesConflictForInstall(requestedType, installedType)) {
                return ModulePolicyDecision.deny(
                        "Module type '" + requestedType + "' is incompatible with installed type '"
                                + installedType + "'");
            }
        }
        return ModulePolicyDecision.allow();
    }
}
