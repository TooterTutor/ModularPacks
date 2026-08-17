package io.github.tootertutor.ModularPacks.modules.policy;

import java.util.function.Function;

import io.github.tootertutor.ModularPacks.config.UpgradeDef;

/**
 * Applies MaxInstalled either to an exact module type or to every module that
 * shares a non-empty InstallGroup.
 */
final class InstallationLimitPolicy implements ModulePolicy {

    private final Function<String, UpgradeDef> definitionResolver;

    InstallationLimitPolicy(Function<String, UpgradeDef> definitionResolver) {
        this.definitionResolver = definitionResolver;
    }

    @Override
    public ModulePolicyDecision canInstall(ModulePolicyContext context) {
        String requestedType = context.moduleType();
        if (requestedType == null || requestedType.isBlank()) {
            return ModulePolicyDecision.deny("Module type is missing");
        }

        UpgradeDef requestedDef = context.moduleDefinition();
        int maximum = requestedDef == null ? 1 : requestedDef.maxInstalled();
        String requestedGroup = requestedDef == null ? null : requestedDef.installGroup();

        long installedCount = context.installedModules().stream()
                .filter(installed -> countsTowardLimit(installed.moduleType(), requestedType, requestedGroup))
                .count();

        if (installedCount >= maximum) {
            String scope = requestedGroup == null
                    ? "module type '" + requestedType + "'"
                    : "install group '" + requestedGroup + "'";
            return ModulePolicyDecision.deny("Maximum installed limit of " + maximum + " reached for " + scope);
        }

        return ModulePolicyDecision.allow();
    }

    private boolean countsTowardLimit(String installedType, String requestedType, String requestedGroup) {
        if (installedType == null) {
            return false;
        }

        if (requestedGroup == null) {
            return requestedType.equalsIgnoreCase(installedType);
        }

        UpgradeDef installedDef = definitionResolver.apply(installedType);
        return installedDef != null
                && installedDef.installGroup() != null
                && requestedGroup.equalsIgnoreCase(installedDef.installGroup());
    }
}
