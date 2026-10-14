package org.lbl.auth.identity;

import java.util.Map;

public record VerifiedIdentity(String providerKey, String issuer, String subject, String displayName,
                               String email, String employeeNo, Map<String, Object> attributes) {
}
