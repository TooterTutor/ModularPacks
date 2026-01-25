package io.github.tootertutor.ModularPacks.commands;

import java.util.List;

import org.bukkit.entity.Player;

/**
 * Base class for subcommands with common functionality for validation and error
 * handling.
 */
public abstract class AbstractSubcommand implements Subcommand {

    /**
     * Get the usage text for this command. Format: "subcommand arg1 arg2
     * [optional]"
     * Override to provide command-specific usage help.
     */
    @Override
    public abstract String getUsage();

    /**
     * Check if the command context has sufficient permissions, sending error if
     * not.
     */
    protected boolean checkPermission(CommandContext ctx) {
        String perm = permission();
        if (perm == null || perm.isBlank())
            return true;
        if (ctx.sender().hasPermission(perm))
            return true;
        ctx.sendError("You do not have permission.");
        return false;
    }

    /**
     * Require the sender to be a player.
     */
    protected Player requirePlayer(CommandContext ctx) {
        Player player = ctx.requirePlayer();
        if (player == null) {
            ctx.sendUsage(getUsage());
        }
        return player;
    }

    /**
     * Require a minimum number of arguments.
     */
    protected boolean requireArgs(CommandContext ctx, int minimum) {
        if (ctx.size() >= minimum)
            return true;
        ctx.sendUsage(getUsage());
        return false;
    }

    /**
     * Resolve a player by name, sending error feedback if not found.
     */
    protected Player resolvePlayer(CommandContext ctx, String name) {
        return ctx.resolvePlayer(name);
    }

    /**
     * Parse an integer safely with fallback.
     */
    protected int parseInt(String value, int defaultValue) {
        return CommandContext.parseInt(value, defaultValue);
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        return List.of();
    }
}
