package org.lbl.file;

import java.nio.file.Path;

/** 仅供后端业务服务消费，Controller 不应把 path 序列化给客户端。 */
public record StagedFile(StagedUpload metadata, Path path) {
}
