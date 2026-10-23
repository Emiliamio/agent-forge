package com.agentforge.fake;

import com.agentforge.service.rag.api.DocumentStoragePort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FakeDocumentStoragePort: 内存驻留式文档对象存储测试替身 (R6 Fake 实现)
 * 纯内存并发映射实现，无需挂载外部文件系统或 MinIO/S3 即可执行闭环测试
 */
public class FakeDocumentStoragePort implements DocumentStoragePort {

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public String store(String path, byte[] content) {
        if (path == null) {
            return null;
        }
        storage.put(path, content != null ? content : new byte[0]);
        return "fake://" + path;
    }

    @Override
    public byte[] load(String path) {
        if (path == null) {
            return null;
        }
        return storage.get(path);
    }

    @Override
    public void delete(String path) {
        if (path != null) {
            storage.remove(path);
        }
    }

    @Override
    public boolean exists(String path) {
        return path != null && storage.containsKey(path);
    }

    public int size() {
        return storage.size();
    }
}
