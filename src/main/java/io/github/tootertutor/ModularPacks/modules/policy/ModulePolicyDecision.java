package io.github.tootertutor.ModularPacks.modules.policy;

/**
 * Result returned by a module mutation policy.
 */
public record ModulePolicyDecision(boolean allowed, String reason) {

    private static final ModulePolicyDecision ALLOW = new ModulePolicyDecision(true, null);

    public static ModulePolicyDecision allow() {
        return ALLOW;
    }

    public static ModulePolicyDecision deny(String reason) {
        return new ModulePolicyDecision(false, reason);
    }
}
