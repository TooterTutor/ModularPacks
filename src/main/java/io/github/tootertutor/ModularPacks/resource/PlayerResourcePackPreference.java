package io.github.tootertutor.ModularPacks.resource;

import java.util.List;
import java.util.UUID;

/** Persisted ordered set of resource-pack layers selected by one player. */
public record PlayerResourcePackPreference(UUID playerId, List<String> enabledPackIds) {

    public PlayerResourcePackPreference {
        enabledPackIds = enabledPackIds == null ? List.of() : List.copyOf(enabledPackIds);
    }
}
