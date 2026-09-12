package com.gov.gw.storage;

import com.gov.gw.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    private final Path root;

    public LocalStorageService(@Value("${app.storage.local-dir:./data/files}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建本地存储目录 " + root, e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new ApiException(400, "非法文件路径");
        }
        return p;
    }

    @Override
    public String put(String key, InputStream in, long size) {
        try {
            Path p = resolve(key);
            Files.createDirectories(p.getParent());
            Files.copy(in, p, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败", e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw ApiException.notFound("文件");
        }
    }

    @Override
    public byte[] getBytes(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw ApiException.notFound("文件");
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }
}
