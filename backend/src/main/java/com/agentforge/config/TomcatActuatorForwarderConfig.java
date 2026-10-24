package com.agentforge.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

/**
 * 根上下文到 /api 上下文的平滑桥接配置
 * 允许外部以 http://host:8080/actuator/** 发起的标准指标采集请求，
 * 透明内部跨上下文 (crossContext) 转发至 http://host:8080/api/actuator/**，
 * 兼顾 OpenAPI / R5 契约架构一致性与行业标准可观测性采集协议。
 */
@Configuration
public class TomcatActuatorForwarderConfig {

    @Bean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory() {
        return new TomcatServletWebServerFactory() {
            @Override
            protected void prepareContext(Host host, ServletContextInitializer[] initializers) {
                super.prepareContext(host, initializers);

                // 1. 开启主上下文跨上下文转发能力
                Context apiContext = (Context) host.findChild("/api");
                if (apiContext != null) {
                    apiContext.setCrossContext(true);
                }

                // 2. 注册 ROOT 根上下文 ("")
                Context rootContext = (Context) host.findChild("");
                if (rootContext == null) {
                    StandardContext root = new StandardContext();
                    root.setPath("");
                    File docBase = new File(System.getProperty("java.io.tmpdir"), "tomcat-root-ctx");
                    if (!docBase.exists()) {
                        docBase.mkdirs();
                    }
                    root.setDocBase(docBase.getAbsolutePath());
                    root.setCrossContext(true);
                    root.addLifecycleListener(new Tomcat.FixContextListener());

                    // 注册 /actuator 转发 Servlet
                    Tomcat.addServlet(root, "actuatorForwardServlet", new HttpServlet() {
                        @Override
                        protected void service(HttpServletRequest req, HttpServletResponse resp)
                                throws ServletException, IOException {
                            String uri = req.getRequestURI();
                            var targetCtx = req.getServletContext().getContext("/api");
                            if (targetCtx != null) {
                                targetCtx.getRequestDispatcher(uri).forward(req, resp);
                            } else {
                                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                            }
                        }
                    });
                    root.addServletMappingDecoded("/actuator/*", "actuatorForwardServlet");
                    host.addChild(root);
                }
            }
        };
    }
}
