package org.lbl.file;

import org.lbl.common.result.Result;
import org.lbl.security.context.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {
    private final StagedFileStorage storage;

    public FileUploadController(StagedFileStorage storage) {
        this.storage = storage;
    }

    @PostMapping("/staged")
    Result<StagedUpload> upload(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal CurrentUser user) {
        return Result.ok(storage.stage(file, ownerId(user)), "文件上传成功");
    }

    @DeleteMapping("/staged/{token}")
    Result<Void> delete(@PathVariable String token, @AuthenticationPrincipal CurrentUser user) {
        storage.delete(token, ownerId(user));
        return Result.ok(null, "暂存文件已删除");
    }

    private static Long ownerId(CurrentUser user) {
        if (user == null) throw new AccessDeniedException("只有正式系统用户可以上传文件");
        return user.id();
    }
}
