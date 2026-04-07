package com.tecozam.bills.shared.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI tecozamBillsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tecozam Bills API")
                        .version("1.0")
                        .description("Sistema integral de control y cotejo de gastos de flota — Tecozam")
                        .contact(new Contact()
                                .name("Tecozam")
                                .email("soporte@tecozam.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT obtenido del endpoint /api/auth/login")));
    }

    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder()
                .group("auth")
                .displayName("Autenticación")
                .pathsToMatch("/api/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi facturasGroup() {
        return GroupedOpenApi.builder()
                .group("facturas")
                .displayName("Facturas")
                .pathsToMatch("/api/facturas/**")
                .build();
    }

    @Bean
    public GroupedOpenApi ticketsGroup() {
        return GroupedOpenApi.builder()
                .group("tickets")
                .displayName("Tickets")
                .pathsToMatch("/api/tickets/**")
                .build();
    }

    @Bean
    public GroupedOpenApi tarjetasGroup() {
        return GroupedOpenApi.builder()
                .group("tarjetas")
                .displayName("Tarjetas")
                .pathsToMatch("/api/tarjetas/**")
                .build();
    }

    @Bean
    public GroupedOpenApi vehiculosGroup() {
        return GroupedOpenApi.builder()
                .group("vehiculos")
                .displayName("Vehículos")
                .pathsToMatch("/api/vehiculos/**")
                .build();
    }

    @Bean
    public GroupedOpenApi trabajadoresGroup() {
        return GroupedOpenApi.builder()
                .group("trabajadores")
                .displayName("Trabajadores")
                .pathsToMatch("/api/trabajadores/**")
                .build();
    }

    @Bean
    public GroupedOpenApi gastosGroup() {
        return GroupedOpenApi.builder()
                .group("gastos")
                .displayName("Gastos")
                .pathsToMatch("/api/gastos/**")
                .build();
    }

    @Bean
    public GroupedOpenApi dashboardGroup() {
        return GroupedOpenApi.builder()
                .group("dashboard")
                .displayName("Dashboard")
                .pathsToMatch("/api/dashboard/**")
                .build();
    }
}
