package com.agentforge.service.rag.api;

/**
 * 六边形架构 - 文档对象持久化与存储端口 (R6 Port)
 * 隔离底层本地文件系统、MinIO、S3 或云对象存储，核心业务只面向该端口
 */
public interface DocumentStoragePort {

    String store(String path, byte[] content);

    byte[] load(String path);

    void delete(String path);

    boolean exists(String path);
}
