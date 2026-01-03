package io.github.tootertutor.ModularPacks.commands;

import java.util.List;

public interface Subcommand {
    String name();

    String description();

    void execute(CommandContext ctx);

    /**
     * Permission node required to use/see this subcommand.
     * <p>
     * Return {@code null} to require no extra permission beyond the base command.
     */
    default String permission() {
        return null;
    }

    default List<String> tabComplete(CommandContext ctx) {
        return List.of();
    }
}
