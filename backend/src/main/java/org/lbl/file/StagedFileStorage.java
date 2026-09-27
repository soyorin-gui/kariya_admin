package org.lbl.file;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件暂存端口。未来切换到 S3/OSS/MinIO 时替换实现即可，导入业务不需要改上传协议。
 */
public interface StagedFileStorage {
    StagedUpload stage(MultipartFile file, Long ownerId);
    StagedFile require(String token, Long ownerId);
    void delete(String token, Long ownerId);
}
