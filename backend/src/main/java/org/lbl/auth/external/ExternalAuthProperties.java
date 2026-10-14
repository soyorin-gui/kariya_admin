package org.lbl.auth.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "lbl.external-auth")
public class ExternalAuthProperties {
    private String backendBaseUrl = "http://localhost:8080";
    private String frontendBaseUrl = "http://localhost:5173";
    /** 外部 OAuth/OIDC 请求使用的可选 HTTP CONNECT 代理；留空时直连。 */
    private String proxyHost = "";
    private int proxyPort;
    private Map<String, Provider> providers = new LinkedHashMap<>();

    public String getBackendBaseUrl() { return backendBaseUrl; }
    public void setBackendBaseUrl(String backendBaseUrl) { this.backendBaseUrl = backendBaseUrl; }
    public String getFrontendBaseUrl() { return frontendBaseUrl; }
    public void setFrontendBaseUrl(String frontendBaseUrl) { this.frontendBaseUrl = frontendBaseUrl; }
    public String getProxyHost() { return proxyHost; }
    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
    public Map<String, Provider> getProviders() { return providers; }
    public void setProviders(Map<String, Provider> providers) { this.providers = providers; }

    public static class Provider {
        private boolean enabled;
        private String displayName;
        private String icon;
        private String protocol = "OAUTH2";
        private String authorizationUri;
        private String tokenUri;
        private String userInfoUri;
        /** 企业统一认证入口（UIAS 专用；OAuth/OIDC 提供方保持为空）。 */
        private String entryUrl;
        /** 企业统一认证回调地址（UIAS 专用）。 */
        private String callbackUrl;
        /** UIAS 携带回调地址时使用的参数名，默认 ssotarget。 */
        private String targetParameter = "ssotarget";
        private String clientId;
        private String clientSecret;
        private List<String> scopes = List.of();
        private String issuer = "";
        private String subjectClaim = "id";
        private String displayNameClaim = "name";
        private String emailClaim = "email";
        private String employeeNoClaim;
        private String clientIdParameter = "client_id";
        private String clientSecretParameter = "client_secret";
        private String tokenMethod = "POST";
        private boolean pkceEnabled = true;
        private String authorizationFragment;
        private Map<String, String> authorizationParameters = new LinkedHashMap<>();
        private Map<String, String> tokenParameters = new LinkedHashMap<>();
        private Map<String, String> userInfoParameters = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getAuthorizationUri() { return authorizationUri; }
        public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = authorizationUri; }
        public String getTokenUri() { return tokenUri; }
        public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
        public String getUserInfoUri() { return userInfoUri; }
        public void setUserInfoUri(String userInfoUri) { this.userInfoUri = userInfoUri; }
        public String getEntryUrl() { return entryUrl; }
        public void setEntryUrl(String entryUrl) { this.entryUrl = entryUrl; }
        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
        public String getTargetParameter() { return targetParameter; }
        public void setTargetParameter(String targetParameter) { this.targetParameter = targetParameter; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getSubjectClaim() { return subjectClaim; }
        public void setSubjectClaim(String subjectClaim) { this.subjectClaim = subjectClaim; }
        public String getDisplayNameClaim() { return displayNameClaim; }
        public void setDisplayNameClaim(String displayNameClaim) { this.displayNameClaim = displayNameClaim; }
        public String getEmailClaim() { return emailClaim; }
        public void setEmailClaim(String emailClaim) { this.emailClaim = emailClaim; }
        public String getEmployeeNoClaim() { return employeeNoClaim; }
        public void setEmployeeNoClaim(String employeeNoClaim) { this.employeeNoClaim = employeeNoClaim; }
        public String getClientIdParameter() { return clientIdParameter; }
        public void setClientIdParameter(String clientIdParameter) { this.clientIdParameter = clientIdParameter; }
        public String getClientSecretParameter() { return clientSecretParameter; }
        public void setClientSecretParameter(String clientSecretParameter) { this.clientSecretParameter = clientSecretParameter; }
        public String getTokenMethod() { return tokenMethod; }
        public void setTokenMethod(String tokenMethod) { this.tokenMethod = tokenMethod; }
        public boolean isPkceEnabled() { return pkceEnabled; }
        public void setPkceEnabled(boolean pkceEnabled) { this.pkceEnabled = pkceEnabled; }
        public String getAuthorizationFragment() { return authorizationFragment; }
        public void setAuthorizationFragment(String authorizationFragment) { this.authorizationFragment = authorizationFragment; }
        public Map<String, String> getAuthorizationParameters() { return authorizationParameters; }
        public void setAuthorizationParameters(Map<String, String> value) { authorizationParameters = value; }
        public Map<String, String> getTokenParameters() { return tokenParameters; }
        public void setTokenParameters(Map<String, String> value) { tokenParameters = value; }
        public Map<String, String> getUserInfoParameters() { return userInfoParameters; }
        public void setUserInfoParameters(Map<String, String> value) { userInfoParameters = value; }
    }
}
