package com.gov.gw.storage;

import com.gov.gw.common.ApiException;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {
    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(
            @Value("${app.storage.minio.endpoint}") String endpoint,
            @Value("${app.storage.minio.access-key}") String accessKey,
            @Value("${app.storage.minio.secret-key}") String secretKey,
            @Value("${app.storage.minio.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String put(String key, InputStream in, long size) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(in, size, -1).build());
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 写入失败：" + e.getMessage(), e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw ApiException.notFound("文件");
        }
    }

    @Override
    public byte[] getBytes(String key) {
        try (InputStream in = get(key)) {
            return in.readAllBytes();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 读取失败", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
