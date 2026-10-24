package com.agentforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Actuator 可观测性端点安全防护过滤器（纵深防御，第二道闸）。
 * 真正的准入控制是【端口隔离】：management.server.port=9090，且 compose 只发布 8080，
 * 因此经 Docker NAT 转发的宿主端口请求根本到不了管理端口，/actuator 对外不可达。
 * 本过滤器在此基础上再加一层：仅允许回环 / RFC1918 内网源的直接套接字连接，
 * 且 X-Forwarded-For 链路上只要出现非内网 IP 就静默返回 404（不暴露端点存在性）。
 * 注意：Docker 桥接网络下外部请求的 remoteAddr 是网桥网关（172.x），单靠本过滤器无法区分内外，
 * 所以不要把管理端口发布到宿主——端口不发布才是真正的边界。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ActuatorGuardFilter extends OncePerRequestFilter {

    private final ClientIpResolver clientIpResolver;

    public ActuatorGuardFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        return !uri.startsWith("/actuator") && !uri.startsWith("/api/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 1. 校验直接套接字来源 IP 是否属于合法内网/回环
        String socketIp = request.getRemoteAddr();
        if (!isInternalOrLoopback(socketIp)) {
            log.warn("🛑 [ACTUATOR_PUBLIC_BLOCKED] 拒绝外网套接字访问监控端点: uri={}, socketIp={}", uri, socketIp);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 2. 校验 ClientIpResolver 解析出的客户端真实 IP
        String clientIp = clientIpResolver != null ? clientIpResolver.resolveClientIp(request) : socketIp;
        if (!isInternalOrLoopback(clientIp)) {
            log.warn("🛑 [ACTUATOR_PUBLIC_BLOCKED] 拒绝外网客户端访问监控端点: uri={}, clientIp={}", uri, clientIp);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 3. 严格防范反向代理透传公网源：若携带 X-Forwarded-For，链路上任何非内网 IP 均坚决静默阻断
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] ips = xff.split(",");
            for (String rawIp : ips) {
                String ip = rawIp.trim();
                if (!ip.isEmpty() && !isInternalOrLoopback(ip)) {
                    log.warn("🛑 [ACTUATOR_PUBLIC_BLOCKED] 拒绝外网代理源访问监控端点: uri={}, xffIp={}", uri, ip);
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判断 IP 是否属于回环地址或 RFC1918 私有内网网段
     */
    public static boolean isInternalOrLoopback(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String cleanIp = ip.trim();
        if ("127.0.0.1".equals(cleanIp) || "0:0:0:0:0:0:0:1".equals(cleanIp) || "::1".equals(cleanIp) || "localhost".equalsIgnoreCase(cleanIp)) {
            return true;
        }

        try {
            InetAddress addr = InetAddress.getByName(cleanIp);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()) {
                return true;
            }
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4) {
                int b0 = bytes[0] & 0xFF;
                int b1 = bytes[1] & 0xFF;
                // 10.0.0.0/8
                if (b0 == 10) return true;
                // 172.16.0.0/12 (172.16.x.x ~ 172.31.x.x)
                if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;
                // 192.168.0.0/16
                if (b0 == 192 && b1 == 168) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
