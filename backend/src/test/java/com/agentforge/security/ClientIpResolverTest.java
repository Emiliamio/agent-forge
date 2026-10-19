package com.agentforge.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ClientIpResolver 真实客户端 IP 解析与防伪造安全测试")
public class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
        // 默认生产安全配置：仅信任回环地址
        ReflectionTestUtils.setField(resolver, "trustedProxyPatterns", List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1"));
        resolver.init();
    }

    @Test
    @DisplayName("直连请求（非受信代理）：拒绝采信客户端伪造的 XFF 与 X-Real-IP，返回物理套接字 IP")
    void testDirectConnectionRejectsSpoofedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.28.80.1"); // 内网/公网客户端直连 8080 端口
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        request.addHeader("X-Real-IP", "9.10.11.12");

        String resolvedIp = resolver.resolveClientIp(request);
        assertEquals("172.28.80.1", resolvedIp, "未经过信任代理直连时，必须严格返回物理 RemoteAddr，杜绝伪造 Header 绕过限流");
    }

    @Test
    @DisplayName("公网直连（非受信代理）：拒绝采信伪造的客户端 IP")
    void testPublicDirectConnectionRejectsSpoofedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.195");
        request.addHeader("X-Forwarded-For", "192.168.1.100");

        String resolvedIp = resolver.resolveClientIp(request);
        assertEquals("203.0.113.195", resolvedIp);
    }

    @Test
    @DisplayName("受信代理转发：从右向左正确剥离受信任节点，提取真实客户端 IP")
    void testTrustedProxyStripsFromRightToLeft() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // 本地 Nginx/网关反代
        // 外部客户端 203.0.113.50 通过 Nginx(127.0.0.1) 转发
        request.addHeader("X-Forwarded-For", "203.0.113.50, 127.0.0.1");

        String resolvedIp = resolver.resolveClientIp(request);
        assertEquals("203.0.113.50", resolvedIp, "受信任反代转发时，应当从右向左剥离信任节点并提取真实客户端 IP");
    }

    @Test
    @DisplayName("多级代理信任剥离：客户端伪造头部 + 受信代理追加，正确识别实际客户端")
    void testMultiHopProxyStripsToRealClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // 受信任反代
        // 客户端 198.51.100.99 自身伪造了 1.1.1.1，经受信任反代转发
        request.addHeader("X-Forwarded-For", "1.1.1.1, 198.51.100.99");

        String resolvedIp = resolver.resolveClientIp(request);
        assertEquals("198.51.100.99", resolvedIp, "从右向左遇到第一个非受信 IP 即停，忽略其左侧伪造的 IP");
    }

    @Test
    @DisplayName("受信代理传递 X-Real-IP 兜底提取")
    void testTrustedProxyXRealIpFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.77");

        String resolvedIp = resolver.resolveClientIp(request);
        assertEquals("198.51.100.77", resolvedIp);
    }

    @Test
    @DisplayName("空请求与空地址边界保护")
    void testNullAndEmptyRequestHandling() {
        assertEquals("127.0.0.1", resolver.resolveClientIp(null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");
        assertEquals("127.0.0.1", resolver.resolveClientIp(request));
    }
}
