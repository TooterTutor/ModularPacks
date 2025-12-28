package io.github.tootertutor.SophiBackpacks.commands;

import java.util.List;

public interface Subcommand {
    String name();

    String description();

    void execute(CommandContext ctx);

    default List<String> tabComplete(CommandContext ctx) {
        return List.of();
    }
}
