package com.gov.gw.storage;

import java.io.InputStream;

/** 正文/附件/PDF/章图 存储抽象。默认本地磁盘，app.storage.type=minio 切换 MinIO */
public interface StorageService {
    /** 写入对象，返回实际 key */
    String put(String key, InputStream in, long size);

    InputStream get(String key);

    byte[] getBytes(String key);

    void delete(String key);

    boolean exists(String key);
}
