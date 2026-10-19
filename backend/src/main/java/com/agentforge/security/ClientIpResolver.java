package com.agentforge.security;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 工业级客户端真实 IP 解析器 (防 Header 伪造与限流击穿)
 * 核心安全防线：
 * 1. 直连请求 (remoteAddr 非受信代理): 100% 拒绝采信客户端伪造的 X-Forwarded-For / X-Real-IP，直接返回物理套接字 IP；
 * 2. 反代请求 (remoteAddr 来自受信内网代理): 从右向左剥离受信任代理跳数，提取首个非受信任的外网真实客户端 IP。
 */
@Slf4j
@Component
public class ClientIpResolver {

    @Value("${agentforge.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
    private List<String> trustedProxyPatterns;

    private final List<CidrMatcher> matchers = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (trustedProxyPatterns != null) {
            for (String pattern : trustedProxyPatterns) {
                String p = pattern.trim();
                if (!p.isEmpty()) {
                    matchers.add(new CidrMatcher(p));
                }
            }
        }
        log.info("🛡️ [SECURITY] ClientIpResolver 已加载 {} 条受信任反向代理网段/IP 白名单规则", matchers.size());
    }

    /**
     * 安全提取客户端真实 IP
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "127.0.0.1";
        }
        remoteAddr = remoteAddr.trim();

        // 1. 若直接连接的套接字 IP 并不在受信任反向代理列表中，坚决拒绝信任任何客户端伪造 Header！
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 2. 直接连接确认来自受信任代理，从右向左安全逐级剥离可信代理节点
        String xff = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(xff) && !"unknown".equalsIgnoreCase(xff)) {
            String[] hops = xff.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!hop.isBlank() && !"unknown".equalsIgnoreCase(hop) && !isTrustedProxy(hop)) {
                    return hop;
                }
            }
        }

        // 3. 兜底检查受信任反代传递的 X-Real-IP
        String xRealIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp) && !isTrustedProxy(xRealIp)) {
            return xRealIp.trim();
        }

        return remoteAddr;
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String cleanIp = ip.trim();
        for (CidrMatcher matcher : matchers) {
            if (matcher.matches(cleanIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IP 与 CIDR 匹配辅助类
     */
    private static class CidrMatcher {
        private final String rawPattern;
        private byte[] networkBytes;
        private int prefixLength = -1;
        private boolean isCidr = false;

        public CidrMatcher(String pattern) {
            this.rawPattern = pattern.trim();
            try {
                if (rawPattern.contains("/")) {
                    String[] parts = rawPattern.split("/");
                    this.isCidr = true;
                    this.prefixLength = Integer.parseInt(parts[1].trim());
                    this.networkBytes = InetAddress.getByName(parts[0].trim()).getAddress();
                } else {
                    this.networkBytes = InetAddress.getByName(rawPattern).getAddress();
                }
            } catch (Exception e) {
                log.warn("无法解析受信任代理规则 [{}]: {}", pattern, e.getMessage());
            }
        }

        public boolean matches(String targetIp) {
            if (networkBytes == null) {
                return rawPattern.equalsIgnoreCase(targetIp);
            }
            try {
                byte[] targetBytes = InetAddress.getByName(targetIp).getAddress();
                if (targetBytes.length != networkBytes.length) {
                    return false;
                }
                if (!isCidr) {
                    for (int i = 0; i < networkBytes.length; i++) {
                        if (networkBytes[i] != targetBytes[i]) return false;
                    }
                    return true;
                }

                // CIDR 掩码位校验
                int fullBytes = prefixLength / 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (networkBytes[i] != targetBytes[i]) return false;
                }
                int remainderBits = prefixLength % 8;
                if (remainderBits > 0 && fullBytes < networkBytes.length) {
                    int mask = 0xFF << (8 - remainderBits);
                    return (networkBytes[fullBytes] & mask) == (targetBytes[fullBytes] & mask);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
