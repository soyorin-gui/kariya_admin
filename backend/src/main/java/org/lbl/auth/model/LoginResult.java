package org.lbl.auth.model;

public record LoginResult(String accessToken, UserProfile user) {
    public record UserProfile(Long id, String username, String realName, boolean passwordChangeRequired) {
    }
}
