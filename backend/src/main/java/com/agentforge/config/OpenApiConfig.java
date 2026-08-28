package com.agentforge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 (Swagger) 接口文档配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String bearerAuthSchemeName = "BearerAuth";
        final String apiKeyAuthSchemeName = "ApiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("AgentForge 企业级 AI 智能体与混合检索 RAG 平台 API")
                        .description("工业级多租户 AI 编排中台，支持知识库混合检索、DAG 工作流调度与 Token 商业化计费。")
                        .version("v1.0.0")
                        .contact(new Contact().name("AgentForge Team").url("https://agentforge.ai").email("support@agentforge.ai"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(bearerAuthSchemeName)
                        .addList(apiKeyAuthSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerAuthSchemeName,
                                new SecurityScheme()
                                        .name(bearerAuthSchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请输入 Bearer Token"))
                        .addSecuritySchemes(apiKeyAuthSchemeName,
                                new SecurityScheme()
                                        .name("X-API-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("请输入专属 API Key (如 af-sk-xxxx)")));
    }
}
