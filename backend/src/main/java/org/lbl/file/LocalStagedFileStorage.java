package org.lbl.file;

import org.lbl.common.exception.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalStagedFileStorage implements StagedFileStorage {
    private final FileUploadProperties properties;
    private final Path root;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public LocalStagedFileStorage(FileUploadProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.stagingDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建文件上传暂存目录: " + root, ex);
        }
    }

    @Override
    public StagedUpload stage(MultipartFile file, Long ownerId) {
        validate(file);
        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = extension(originalName);
        String token = UUID.randomUUID().toString();
        Path temporary = root.resolve(token + ".part");
        Path target = root.resolve(token + "." + extension);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Instant expiresAt = Instant.now().plus(properties.ttl());
            StagedUpload metadata = new StagedUpload(token, originalName, normalizedContentType(file), file.getSize(), HexFormat.of().formatHex(digest.digest()), expiresAt);
            entries.put(token, new Entry(ownerId, metadata, target));
            return metadata;
        } catch (IOException | NoSuchAlgorithmException ex) {
            quietlyDelete(temporary);
            quietlyDelete(target);
            throw new BusinessException("文件暂存失败，请稍后重试");
        }
    }

    @Override
    public StagedFile require(String token, Long ownerId) {
        Entry entry = ownedEntry(token, ownerId);
        if (entry.metadata().expiresAt().isBefore(Instant.now()) || !Files.isRegularFile(entry.path())) {
            entries.remove(token);
            quietlyDelete(entry.path());
            throw new BusinessException("上传文件已过期，请重新上传");
        }
        return new StagedFile(entry.metadata(), entry.path());
    }

    @Override
    public void delete(String token, Long ownerId) {
        Entry entry = ownedEntry(token, ownerId);
        entries.remove(token);
        quietlyDelete(entry.path());
    }

    /** 清理过期记录，也清理应用异常重启遗留超过 TTL 的孤儿文件。 */
    @Scheduled(fixedDelayString = "${lbl.file-upload.cleanup-interval-ms:600000}")
    void cleanup() {
        Instant now = Instant.now();
        entries.forEach((token, entry) -> {
            if (entry.metadata().expiresAt().isBefore(now) && entries.remove(token, entry)) quietlyDelete(entry.path());
        });
        try (var paths = Files.list(root)) {
            paths.filter(Files::isRegularFile).filter(path -> {
                try { return Files.getLastModifiedTime(path).toInstant().plus(properties.ttl()).isBefore(now); }
                catch (IOException ex) { return false; }
            }).forEach(LocalStagedFileStorage::quietlyDelete);
        } catch (IOException ignored) {
            // 下一轮清理会重试；上传主流程不应因维护任务失败而中断。
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择需要上传的文件");
        if (file.getSize() > properties.maxBytes()) throw new BusinessException("文件大小不能超过 " + properties.maxBytes() / 1024 / 1024 + " MB");
        String extension = extension(safeOriginalName(file.getOriginalFilename()));
        if (!properties.extensionSet().contains(extension)) throw new BusinessException("不支持该文件格式，允许格式：" + String.join("、", properties.extensionSet()));
        if (!properties.contentTypeSet().contains(normalizedContentType(file))) throw new BusinessException("文件内容类型不受支持");
    }

    private Entry ownedEntry(String token, Long ownerId) {
        Entry entry = entries.get(token);
        if (entry == null || !entry.ownerId().equals(ownerId)) throw new BusinessException("上传文件不存在或无权访问");
        return entry;
    }

    private static String safeOriginalName(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) throw new BusinessException("文件名无效");
        String normalized = value.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.length() > 180) throw new BusinessException("文件名过长");
        return name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 1 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private static String normalizedContentType(MultipartFile file) {
        if (file.getContentType() == null) return "application/octet-stream";
        return file.getContentType().split(";", 2)[0].trim().toLowerCase();
    }

    private static void quietlyDelete(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private record Entry(Long ownerId, StagedUpload metadata, Path path) { }
}
