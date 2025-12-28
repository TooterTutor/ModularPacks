package io.github.tootertutor.ModularPacks.commands;

import java.util.List;

import org.bukkit.command.CommandSender;

public record CommandContext(
        CommandSender sender,
        List<String> args) {
    public String arg(int index) {
        return index < args.size() ? args.get(index) : null;
    }

    public int size() {
        return args.size();
    }
}
