package org.lbl.agent.domain;

import java.util.Objects;
import java.util.Set;

/** Immutable policy for one assistant persona and its tool allowlist. */
public record AgentProfile(String id, String name, String instructions, Set<String> allowedToolNames) {
    public AgentProfile {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Profile id must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Profile name must not be blank");
        if (instructions == null || instructions.isBlank()) throw new IllegalArgumentException("Profile instructions must not be blank");
        allowedToolNames = Set.copyOf(Objects.requireNonNull(allowedToolNames, "allowedToolNames"));
    }

    public boolean allows(String toolName) {
        return allowedToolNames.contains(toolName);
    }
}