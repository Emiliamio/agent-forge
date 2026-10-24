package com.agentforge.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActuatorGuardFilterTest: 可观测性端点白名单限源与公网 404 防护测试")
public class ActuatorGuardFilterTest {

    @Mock
    private ClientIpResolver clientIpResolver;

    @Test
    @DisplayName("IP 判定：正确识别本地回环与 RFC1918 私网网段")
    void testIsInternalOrLoopback() {
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("127.0.0.1"));
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("::1"));
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("0:0:0:0:0:0:0:1"));
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("localhost"));
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("10.0.1.25"));
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("172.18.0.3"));
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("172.31.255.254"));
        assertTrue(ActuatorGuardFilter.isInternalOrLoopback("192.168.0.100"));

        assertFalse(ActuatorGuardFilter.isInternalOrLoopback("203.0.113.199"));
        assertFalse(ActuatorGuardFilter.isInternalOrLoopback("8.8.8.8"));
        assertFalse(ActuatorGuardFilter.isInternalOrLoopback("114.114.114.114"));
        assertFalse(ActuatorGuardFilter.isInternalOrLoopback(null));
        assertFalse(ActuatorGuardFilter.isInternalOrLoopback(""));
    }

    @Test
    @DisplayName("内网请求：来自 127.0.0.1 的 Actuator 请求顺利放行")
    void testInternalRequestAllowed() throws Exception {
        ActuatorGuardFilter filter = new ActuatorGuardFilter(clientIpResolver);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("127.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest());
    }

    @Test
    @DisplayName("公网请求静默阻断：来自公网 IP 的 Actuator 请求直接返回 404")
    void testPublicRequestBlockedWith404() throws Exception {
        ActuatorGuardFilter filter = new ActuatorGuardFilter(clientIpResolver);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("203.0.113.199");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRemoteAddr("203.0.113.199");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(404, response.getStatus());
        assertNull(filterChain.getRequest());
    }

    @Test
    @DisplayName("伪造公网 XFF 请求阻断：经反代识别为公网源的请求直接返回 404")
    void testForgedPublicHeaderBlockedWith404() throws Exception {
        ActuatorGuardFilter filter = new ActuatorGuardFilter(clientIpResolver);
        when(clientIpResolver.resolveClientIp(any())).thenReturn("198.51.100.22");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.22");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(404, response.getStatus());
        assertNull(filterChain.getRequest());
    }
}
