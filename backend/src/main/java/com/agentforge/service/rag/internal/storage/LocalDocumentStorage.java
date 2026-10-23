package com.agentforge.service.rag.internal.storage;

import com.agentforge.service.rag.api.DocumentStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 默认本地文件系统存储适配器 (DocumentStoragePort 实现)
 */
@Slf4j
@Component
public class LocalDocumentStorage implements DocumentStoragePort {

    private final Path rootDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentforge-storage");

    public LocalDocumentStorage() {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            log.warn("无法创建默认文档存储根目录: {}", rootDir, e);
        }
    }

    @Override
    public String store(String path, byte[] content) {
        if (path == null) {
            return null;
        }
        try {
            Path target = rootDir.resolve(path).normalize();
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, content != null ? content : new byte[0]);
            return target.toString();
        } catch (IOException e) {
            log.error("本地文档持久化存储失败: path={}", path, e);
            return null;
        }
    }

    @Override
    public byte[] load(String path) {
        if (path == null) {
            return null;
        }
        try {
            Path target = rootDir.resolve(path).normalize();
            if (Files.exists(target)) {
                return Files.readAllBytes(target);
            }
        } catch (IOException e) {
            log.warn("读取本地持久化文档失败: path={}", path, e);
        }
        return null;
    }

    @Override
    public void delete(String path) {
        if (path == null) {
            return;
        }
        try {
            Path target = rootDir.resolve(path).normalize();
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除本地持久化文档失败: path={}", path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        if (path == null) {
            return false;
        }
        Path target = rootDir.resolve(path).normalize();
        return Files.exists(target);
    }
}
