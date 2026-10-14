package org.lbl.config;

import org.lbl.auth.external.ExternalAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置第三方登录协议定义。
 *
 * <p>授权地址、scope、claim 名称等是应用代码的一部分，不随部署环境变化，因此集中放在 Java 中。
 * enabled、clientId、clientSecret、内网入口等运行时信息仍由 {@link ExternalAuthProperties} 绑定，
 * 密钥绝不能写进本类。</p>
 */
@Configuration
public class ExternalAuthProviderConfig {

    @Bean
    public ExternalAuthProviderRegistry externalAuthProviderRegistry(ExternalAuthProperties runtime) {
        return new ExternalAuthProviderRegistry(builtinProviders(), runtime.getProviders());
    }

    private Map<String, ExternalAuthProperties.Provider> builtinProviders() {
        Map<String, ExternalAuthProperties.Provider> providers = new LinkedHashMap<>();
        providers.put("github", github());
        providers.put("google", google());
        providers.put("wechat", wechat());
        providers.put("uias", uias());
        return providers;
    }

    private ExternalAuthProperties.Provider github() {
        ExternalAuthProperties.Provider provider = base("GitHub", "github", "OAUTH2");
        provider.setAuthorizationUri("https://github.com/login/oauth/authorize");
        provider.setTokenUri("https://github.com/login/oauth/access_token");
        provider.setUserInfoUri("https://api.github.com/user");
        provider.setScopes(List.of("read:user", "user:email"));
        provider.setSubjectClaim("id");
        provider.setDisplayNameClaim("login");
        provider.setEmailClaim("email");
        return provider;
    }

    private ExternalAuthProperties.Provider google() {
        ExternalAuthProperties.Provider provider = base("Google", "google", "OIDC");
        provider.setAuthorizationUri("https://accounts.google.com/o/oauth2/v2/auth");
        provider.setTokenUri("https://oauth2.googleapis.com/token");
        provider.setUserInfoUri("https://openidconnect.googleapis.com/v1/userinfo");
        provider.setIssuer("https://accounts.google.com");
        provider.setScopes(List.of("openid", "profile", "email"));
        provider.setSubjectClaim("sub");
        provider.setDisplayNameClaim("name");
        provider.setEmailClaim("email");
        return provider;
    }

    private ExternalAuthProperties.Provider wechat() {
        ExternalAuthProperties.Provider provider = base("微信", "wechat", "OAUTH2");
        provider.setAuthorizationUri("https://open.weixin.qq.com/connect/qrconnect");
        provider.setTokenUri("https://api.weixin.qq.com/sns/oauth2/access_token");
        provider.setUserInfoUri("https://api.weixin.qq.com/sns/userinfo");
        provider.setClientIdParameter("appid");
        provider.setClientSecretParameter("secret");
        provider.setTokenMethod("GET");
        provider.setPkceEnabled(false);
        provider.setAuthorizationFragment("wechat_redirect");
        provider.setScopes(List.of("snsapi_login"));
        provider.setSubjectClaim("unionid");
        provider.setDisplayNameClaim("nickname");
        provider.setAuthorizationParameters(Map.of("response_type", "code"));
        provider.setTokenParameters(Map.of("grant_type", "authorization_code"));
        provider.setUserInfoParameters(Map.of(
                "access_token", "{access_token}",
                "openid", "{openid}",
                "lang", "zh_CN"));
        return provider;
    }

    private ExternalAuthProperties.Provider uias() {
        ExternalAuthProperties.Provider provider = base("内部统一认证", "enterprise", "SAML");
        provider.setIssuer("uias");
        provider.setTargetParameter("ssotarget");
        return provider;
    }

    private ExternalAuthProperties.Provider base(String name, String icon, String protocol) {
        ExternalAuthProperties.Provider provider = new ExternalAuthProperties.Provider();
        provider.setDisplayName(name);
        provider.setIcon(icon);
        provider.setProtocol(protocol);
        return provider;
    }
}
