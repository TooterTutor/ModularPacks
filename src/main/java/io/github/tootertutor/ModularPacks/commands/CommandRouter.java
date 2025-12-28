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

public final class CommandRouter implements CommandExecutor, TabCompleter {

    private final ModularPacksPlugin plugin;
    private final Map<String, Subcommand> subs = new HashMap<>();

    public CommandRouter(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Subcommand sub) {
        subs.put(sub.name().toLowerCase(Locale.ROOT), sub);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] argsArr) {
        List<String> args = Arrays.asList(argsArr);
        CommandContext ctx = new CommandContext(sender, args);

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /" + label + " <subcommand>"));
            sender.sendMessage(Component.text("Subcommands: " + String.join(", ", subs.keySet())));
            return true;
        }

        Subcommand sub = subs.get(args.get(0).toLowerCase(Locale.ROOT));
        if (sub == null) {
            sender.sendMessage(Component.text("Unknown subcommand. Available: " + String.join(", ", subs.keySet())));
            return true;
        }

        sub.execute(new CommandContext(sender, args.subList(1, args.size())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] argsArr) {
        List<String> args = Arrays.asList(argsArr);
        if (args.size() <= 1) {
            String prefix = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
            return subs.keySet().stream().filter(s -> s.startsWith(prefix)).sorted().toList();
        }

        Subcommand sub = subs.get(args.get(0).toLowerCase(Locale.ROOT));
        if (sub == null)
            return List.of();

        return sub.tabComplete(new CommandContext(sender, Arrays.asList(argsArr).subList(1, args.size())));
    }
}
