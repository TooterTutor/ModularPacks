package io.github.tootertutor.ModularPacks.modules.policy;

/**
 * A composable policy governing module installation and removal.
 */
public interface ModulePolicy {

    default ModulePolicyDecision canInstall(ModulePolicyContext context) {
        return ModulePolicyDecision.allow();
    }

    default ModulePolicyDecision canRemove(ModulePolicyContext context) {
        return ModulePolicyDecision.allow();
    }
}
