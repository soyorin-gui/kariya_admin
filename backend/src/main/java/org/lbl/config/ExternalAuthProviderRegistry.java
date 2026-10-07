package org.lbl.config;

import org.lbl.auth.external.ExternalAuthProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.util.StringUtils.hasText;

/** 把代码中的固定协议定义与部署环境中的开关、凭据和内网地址合并为最终可用配置。 */
public final class ExternalAuthProviderRegistry {
    private final Map<String, ExternalAuthProperties.Provider> providers;

    public ExternalAuthProviderRegistry(Map<String, ExternalAuthProperties.Provider> builtins,
                                        Map<String, ExternalAuthProperties.Provider> runtime) {
        Map<String, ExternalAuthProperties.Provider> merged = new LinkedHashMap<>();
        builtins.forEach((key, definition) -> merged.put(key,
                merge(definition, runtime == null ? null : runtime.get(key))));
        if (runtime != null) {
            runtime.forEach((key, value) -> merged.putIfAbsent(key, value));
        }
        this.providers = Collections.unmodifiableMap(merged);
    }

    public Map<String, ExternalAuthProperties.Provider> all() {
        return providers;
    }

    public ExternalAuthProperties.Provider find(String key) {
        return providers.get(key);
    }

    private ExternalAuthProperties.Provider merge(ExternalAuthProperties.Provider definition,
                                                   ExternalAuthProperties.Provider runtime) {
        if (runtime == null) return definition;
        definition.setEnabled(runtime.isEnabled());
        definition.setClientId(runtime.getClientId());
        definition.setClientSecret(runtime.getClientSecret());
        if (hasText(runtime.getEntryUrl())) definition.setEntryUrl(runtime.getEntryUrl());
        if (hasText(runtime.getCallbackUrl())) definition.setCallbackUrl(runtime.getCallbackUrl());
        if (hasText(runtime.getIssuer())) definition.setIssuer(runtime.getIssuer());
        return definition;
    }
}
