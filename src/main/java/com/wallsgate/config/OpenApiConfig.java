package com.wallsgate.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 / Swagger configuration for the walls-gate runtime API.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_ADMIN_TOKEN = "AdminTokenAuth";
    public static final String SECURITY_USER_ID = "UserIdentityHeader";
    public static final String SECURITY_SESSION_ID = "SessionContextHeader";
    public static final String SECURITY_TRACE_ID = "TraceContextHeader";

    @Bean
    public OpenAPI wallsGateOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("walls-gate API")
                        .version("0.1.0")
                        .description("Ethical-Wall-Aware Gateway & Agent SDK Runtime for Local LLMs. "
                                + "Enforces information barriers, permission-aware retrieval, "
                                + "DLP/anonymization, and token governance before queries reach model inference.")
                        .contact(new Contact()
                                .name("Engineering Team")
                                .email("engineering@wallsgate.io"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("/").description("Current Gateway Host")
                ))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_ADMIN_TOKEN, new SecurityScheme()
                                .name("X-Admin-Token")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Master administrative bearer key required for /admin/** endpoints."))
                        .addSecuritySchemes(SECURITY_USER_ID, new SecurityScheme()
                                .name("X-User-Id")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Enterprise caller identity used to resolve authorized matter access scopes."))
                        .addSecuritySchemes(SECURITY_SESSION_ID, new SecurityScheme()
                                .name("X-Session-Id")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Session or conversation identifier tracking multi-turn ethical wall boundaries."))
                        .addSecuritySchemes(SECURITY_TRACE_ID, new SecurityScheme()
                                .name("X-Trace-Id")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Distributed trace correlation ID propagated to SLF4J MDC.")));
    }
}
