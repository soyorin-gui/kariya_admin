package org.kariya.auth.session;

import java.io.Serializable;

public record LoginSession(Long userId, String username, long authVersion, boolean rememberMe) implements Serializable {
}
