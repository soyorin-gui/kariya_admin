package org.lbl.auth.model;

public record SessionGrant(String sid, String accessToken, LoginResult.UserProfile user, boolean onboarding) {
}
