package io.github.tootertutor.ModularPacks.commands;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * Context for command execution with utilities for argument parsing and
 * validation.
 */
public record CommandContext(
        CommandSender sender,
        List<String> args) {

    /**
     * Get argument at index, or null if not present.
     */
    public String arg(int index) {
        return index < args.size() ? args.get(index) : null;
    }

    /**
     * Number of arguments.
     */
    public int size() {
        return args.size();
    }

    /**
     * Check if sender has a permission, sending error if not.
     * 
     * @return true if allowed, false otherwise
     */
    public boolean require(String permission) {
        if (permission == null || permission.isBlank())
            return true;
        if (sender.hasPermission(permission))
            return true;
        sender.sendMessage(Component.text("You do not have permission for that command."));
        return false;
    }

    /**
     * Require sender to be a player, sending error if not.
     * 
     * @return the player, or null if not a player
     */
    public Player requirePlayer() {
        if (sender instanceof Player p)
            return p;
        sender.sendMessage(Component.text("This command must be run by a player."));
        return null;
    }

    /**
     * Require at least N arguments, sending usage error if not.
     * 
     * @return true if satisfied, false otherwise
     */
    public boolean requireArgs(int minimum, String usage) {
        if (args.size() >= minimum)
            return true;
        sender.sendMessage(Component.text("Usage: " + usage));
        return false;
    }

    /**
     * Resolve a player by name, sending error if not found.
     * 
     * @param name player name
     * @return the player, or null if not found
     */
    public Player resolvePlayer(String name) {
        if (name == null || name.isBlank()) {
            sender.sendMessage(Component.text("Player name cannot be empty."));
            return null;
        }
        Player player = Bukkit.getPlayer(name);
        if (player == null) {
            sender.sendMessage(Component.text("Player not found: " + name));
        }
        return player;
    }

    /**
     * Parse an integer from a string, with fallback default.
     * 
     * @param value        the string to parse
     * @param defaultValue fallback if parsing fails
     * @return the parsed integer or default
     */
    public static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank())
            return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Send usage message to the sender.
     */
    public void sendUsage(String usage) {
        sender.sendMessage(Component.text("Usage: " + usage));
    }

    /**
     * Send error message to the sender.
     */
    public void sendError(String message) {
        sender.sendMessage(Component.text(message));
    }

    /**
     * Send info message to the sender.
     */
    public void sendInfo(String message) {
        sender.sendMessage(Component.text(message));
    }
}
