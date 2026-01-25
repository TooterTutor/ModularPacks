package io.github.tootertutor.ModularPacks.commands;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import net.kyori.adventure.text.Component;

/**
 * Routes commands to appropriate subcommand handlers with help support.
 */
public final class CommandRouter implements CommandExecutor, TabCompleter {

    private final Map<String, Subcommand> subs = new HashMap<>();
    private String mainCommand = "backpack"; // Used in help text

    public CommandRouter(ModularPacksPlugin plugin) {
        // Plugin parameter available for future extension
    }

    /**
     * Register a subcommand.
     */
    public void register(Subcommand sub) {
        subs.put(sub.name().toLowerCase(Locale.ROOT), sub);
    }

    /**
     * Set the main command name (used in help text).
     */
    public void setMainCommand(String name) {
        this.mainCommand = name;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] argsArr) {
        List<String> args = Arrays.asList(argsArr);

        // No arguments: show help
        if (args.isEmpty()) {
            showHelp(sender);
            return true;
        }

        // Check for help request
        String firstArg = args.get(0).toLowerCase(Locale.ROOT);
        if ("help".equals(firstArg) || "?".equals(firstArg)) {
            String subName = args.size() > 1 ? args.get(1).toLowerCase(Locale.ROOT) : null;
            showHelp(sender, subName);
            return true;
        }

        Subcommand sub = subs.get(firstArg);
        if (sub == null) {
            sender.sendMessage(Component.text("Unknown subcommand: " + firstArg));
            sender.sendMessage(Component.text("Type '/backpack help' for available commands."));
            return true;
        }

        // Check permission before executing
        String perm = sub.permission();
        if (perm != null && !perm.isBlank() && !sender.hasPermission(perm)) {
            sender.sendMessage(Component.text("You do not have permission."));
            return true;
        }

        // Execute with remaining arguments
        sub.execute(new CommandContext(sender, args.subList(1, args.size())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] argsArr) {
        List<String> args = Arrays.asList(argsArr);

        // Tab complete first argument: subcommand names
        if (args.size() <= 1) {
            String prefix = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
            return subs.entrySet().stream()
                    .filter(e -> canUse(sender, e.getValue()))
                    .map(Map.Entry::getKey)
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        // Tab complete arguments for specific subcommand
        Subcommand sub = subs.get(args.get(0).toLowerCase(Locale.ROOT));
        if (sub == null)
            return List.of();
        if (!canUse(sender, sub))
            return List.of();

        return sub.tabComplete(new CommandContext(sender, args.subList(1, args.size())));
    }

    /**
     * Show help for all commands or a specific command.
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== ModularPacks Commands ==="));

        for (Subcommand sub : subs.values()) {
            if (!canUse(sender, sub))
                continue;

            String name = sub.name();
            String desc = sub.description();
            String perm = sub.permission();

            String permInfo = (perm != null && !perm.isBlank()) ? " [needs " + perm + "]" : "";
            sender.sendMessage(Component.text("/" + mainCommand + " " + name + " - " + desc + permInfo));
        }

        sender.sendMessage(Component.text("Type '/" + mainCommand + " help <command>' for more info."));
    }

    /**
     * Show help for a specific subcommand.
     */
    private void showHelp(CommandSender sender, String subName) {
        if (subName == null || subName.isBlank()) {
            showHelp(sender);
            return;
        }

        Subcommand sub = subs.get(subName.toLowerCase(Locale.ROOT));
        if (sub == null) {
            sender.sendMessage(Component.text("Unknown command: " + subName));
            return;
        }

        if (!canUse(sender, sub)) {
            sender.sendMessage(Component.text("You do not have permission."));
            return;
        }

        sender.sendMessage(Component.text("=== " + sub.name() + " ==="));
        sender.sendMessage(Component.text(sub.description()));
        sender.sendMessage(Component.text("Usage: " + sub.getUsage()));

        String extended = sub.getExtendedHelp();
        if (extended != null && !extended.isBlank()) {
            sender.sendMessage(Component.text(extended));
        }
    }

    private static boolean canUse(CommandSender sender, Subcommand sub) {
        if (sender == null || sub == null)
            return false;
        String perm = sub.permission();
        if (perm == null || perm.isBlank())
            return true;
        return sender.hasPermission(perm);
    }
}
