package org.lbl.security.context;

import java.security.Principal;

public record OnboardingPrincipal(String onboardingId, String providerKey, String displayName) implements Principal {
    @Override
    public String getName() {
        return "onboarding:" + onboardingId;
    }
}
