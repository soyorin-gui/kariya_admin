package org.lbl.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通用上传暂存区配置。这里的白名单只做第一层拦截，业务导入服务仍必须校验文件内容。
 */
@ConfigurationProperties(prefix = "lbl.file-upload")
public record FileUploadProperties(
        @DefaultValue("./data/uploads/staging") String stagingDirectory,
        @DefaultValue("10485760") long maxBytes,
        @DefaultValue("csv,xlsx") String allowedExtensions,
        @DefaultValue("text/csv,application/csv,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/octet-stream") String allowedContentTypes,
        @DefaultValue("PT30M") Duration ttl) {

    public Set<String> extensionSet() { return split(allowedExtensions); }
    public Set<String> contentTypeSet() { return split(allowedContentTypes); }

    private static Set<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).map(String::toLowerCase)
                .filter(item -> !item.isBlank()).collect(Collectors.toUnmodifiableSet());
    }
}
