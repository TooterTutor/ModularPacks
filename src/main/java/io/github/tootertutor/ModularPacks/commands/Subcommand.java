package io.github.tootertutor.ModularPacks.commands;

import java.util.List;

/**
 * Interface for a subcommand in the command structure.
 */
public interface Subcommand {
    /**
     * The name of the subcommand (used to invoke it).
     */
    String name();

    /**
     * Short description of what this command does.
     */
    String description();

    /**
     * Execute the command with the given context.
     */
    void execute(CommandContext ctx);

    /**
     * Permission node required to use/see this subcommand.
     * Return {@code null} to require no extra permission beyond the base command.
     */
    default String permission() {
        return null;
    }

    /**
     * Tab completion suggestions for this command.
     */
    default List<String> tabComplete(CommandContext ctx) {
        return List.of();
    }

    /**
     * Get the usage text for help display.
     * Format: "backpack subcommand arg1 arg2 [optional]"
     * Override to provide command-specific usage help.
     */
    default String getUsage() {
        return "backpack " + name();
    }

    /**
     * Get extended help text for this command (shown in help display).
     * Return empty string or null for no extended help.
     */
    default String getExtendedHelp() {
        return null;
    }
}
