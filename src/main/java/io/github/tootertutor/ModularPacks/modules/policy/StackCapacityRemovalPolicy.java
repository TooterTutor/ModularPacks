package io.github.tootertutor.ModularPacks.modules.policy;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.UpgradeDef;
import io.github.tootertutor.ModularPacks.storage.BackpackStorage;

/** Rejects Stack Upgrade removal when any logical slot would be over capacity. */
final class StackCapacityRemovalPolicy implements ModulePolicy {

    private final ModularPacksPlugin plugin;

    StackCapacityRemovalPolicy(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ModulePolicyDecision canRemove(ModulePolicyContext context) {
        UpgradeDef definition = context.moduleDefinition();
        if (definition == null || definition.stackMultiplier() <= 1) {
            return ModulePolicyDecision.allow();
        }

        BackpackStorage storage = plugin.backpackStorage().load(context.backpackData());
        var violation = plugin.stackCapacity().firstViolation(
                context.backpackData(), storage, context.moduleId());
        if (violation != null) {
            return ModulePolicyDecision.deny(
                    "Cannot remove Stack Upgrade: slot " + violation.slot() + " stores "
                            + violation.storedCount() + " items, but resulting capacity would be "
                            + violation.capacity() + ".");
        }
        return ModulePolicyDecision.allow();
    }
}
