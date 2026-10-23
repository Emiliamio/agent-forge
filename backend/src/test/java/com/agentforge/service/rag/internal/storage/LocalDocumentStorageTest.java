package com.agentforge.service.rag.internal.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalDocumentStorage: 本地文件存储适配器单元测试")
public class LocalDocumentStorageTest {

    private final LocalDocumentStorage storage = new LocalDocumentStorage();

    @Test
    @DisplayName("测试完整生命周期: 存储、加载、判断存在、删除")
    void testLifecycle() {
        String path = "test-tenant/1001/sample.txt";
        byte[] content = "Hello Local Storage".getBytes(StandardCharsets.UTF_8);

        // 存储
        String location = storage.store(path, content);
        assertNotNull(location);

        // 存在判断
        assertTrue(storage.exists(path));

        // 加载
        byte[] loaded = storage.load(path);
        assertNotNull(loaded);
        assertArrayEquals(content, loaded);

        // 删除
        storage.delete(path);
        assertFalse(storage.exists(path));
        assertNull(storage.load(path));
    }

    @Test
    @DisplayName("测试边界条件: 空路径、空内容、不存在的文件")
    void testEdgeCases() {
        assertNull(storage.store(null, new byte[0]));
        assertNull(storage.load(null));
        assertNull(storage.load("non-existent-file.bin"));
        assertFalse(storage.exists(null));
        assertFalse(storage.exists("non-existent-file.bin"));

        // 删除不存在文件不抛异常
        assertDoesNotThrow(() -> storage.delete(null));
        assertDoesNotThrow(() -> storage.delete("non-existent-file.bin"));

        // 存储空内容
        String emptyPath = "test-tenant/1001/empty.txt";
        String loc = storage.store(emptyPath, null);
        assertNotNull(loc);
        assertTrue(storage.exists(emptyPath));
        byte[] loaded = storage.load(emptyPath);
        assertNotNull(loaded);
        assertEquals(0, loaded.length);
        storage.delete(emptyPath);
    }
}
