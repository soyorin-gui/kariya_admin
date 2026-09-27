package org.lbl.file;

import java.time.Instant;

/** 上传成功后返回给前端的安全元数据；服务端路径不会暴露。 */
public record StagedUpload(String token, String originalName, String contentType, long size,
                           String sha256, Instant expiresAt) {
}
