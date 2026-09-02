package com.tickify.config;

import com.tickify.config.properties.TickifyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableConfigurationProperties(TickifyProperties.class)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TickifyProperties properties;

    /**
     * The React app runs on a different origin in development, and is served from this
     * jar in production. Allowing only the configured origins keeps the credentialed
     * requests working in the first case without opening the API up in the second.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/actuator/**", config);
        return source;
    }

    /**
     * When the frontend is bundled into the jar (-Pfrontend), client-side routes such as
     * /events/{id} have no server-side mapping. Forward them to the SPA entry point so a
     * hard refresh on a deep link does not 404.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{path:^(?!api|actuator|swagger-ui|v3|assets)[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{path:^(?!api|actuator|swagger-ui|v3|assets)[^\\.]*}/**")
                .setViewName("forward:/index.html");
    }
}
