package org.lbl.agent.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves the default assistant profile and validates profile identifiers at startup. */
public class AgentProfileRegistry {
    private final Map<String, AgentProfile> profiles;
    private final String defaultProfileId;

    public AgentProfileRegistry(List<AgentProfile> profiles, String defaultProfileId) {
        Map<String, AgentProfile> index = new LinkedHashMap<>();
        for (AgentProfile profile : profiles) {
            AgentProfile previous = index.putIfAbsent(profile.id(), profile);
            if (previous != null) throw new IllegalStateException("Duplicate agent profile: " + profile.id());
        }
        if (index.isEmpty()) throw new IllegalStateException("At least one agent profile is required");
        if (!index.containsKey(defaultProfileId)) {
            throw new IllegalStateException("Unknown default agent profile: " + defaultProfileId);
        }
        this.profiles = Map.copyOf(index);
        this.defaultProfileId = defaultProfileId;
    }

    public AgentProfile defaultProfile() {
        return profiles.get(defaultProfileId);
    }

    public AgentProfile resolve(String profileId) {
        if (profileId == null || profileId.isBlank()) return defaultProfile();
        AgentProfile profile = profiles.get(profileId);
        if (profile == null) throw new IllegalArgumentException("Unknown agent profile: " + profileId);
        return profile;
    }
    public AgentProfile find(String id) {
        return profiles.get(id);
    }
}