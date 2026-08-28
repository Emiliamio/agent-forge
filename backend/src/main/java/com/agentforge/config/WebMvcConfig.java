package com.agentforge.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.agentforge.security.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 全局配置
 * 注册租户拦截器、Sa-Token 鉴权拦截器与 CORS 跨域规则
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Value("${agentforge.security.white-list:}")
    private List<String> whiteList;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 租户上下文拦截器 (最高优先级，所有请求都需解析租户)
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**")
                .order(Integer.MIN_VALUE);

        // 2. Sa-Token 鉴权拦截器 (白名单放行)
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(whiteList)
                .excludePathPatterns(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/doc.html",
                        "/webjars/**",
                        "/favicon.ico"
                )
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
