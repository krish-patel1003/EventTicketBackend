package com.tickify.user.config;

import com.tickify.user.exception.JwtAuthenticationEntryPoint;
import com.tickify.user.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT security.
 *
 * <p>Authorization comes from one place: the {@code roles} claim in the access token, mapped
 * to {@code ROLE_*} authorities by {@link #jwtAuthenticationConverter()}. An earlier version
 * also ran a hand-written filter that loaded roles from the database — with two filters
 * writing the SecurityContext, the resource server's ran last and replaced the database roles
 * with the token's (empty) scopes, so every {@code hasRole(...)} check failed.
 *
 * <p>{@link EnableMethodSecurity} is what makes those {@code @PreAuthorize} annotations
 * enforce anything at all: without it Spring registers no method interceptor and every
 * annotation on every controller is silently ignored.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Any GET that is not an API or management call: the SPA's HTML shell and static assets.
     */
    private static final RequestMatcher SPA_SHELL = request ->
            HttpMethod.GET.matches(request.getMethod())
                    && !request.getRequestURI().startsWith("/api/")
                    && !request.getRequestURI().startsWith("/actuator/");

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   JwtAuthenticationEntryPoint authenticationEntryPoint,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // The bundled React app: its shell and assets are public, the data
                        // behind them is not. Matching on "a GET outside /api and /actuator"
                        // rather than listing routes keeps this correct as the SPA grows —
                        // client-side routes have no server mapping, so a hard refresh on
                        // /events must still return index.html rather than 401.
                        .requestMatchers(SPA_SHELL).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));

        return http.build();
    }

    /**
     * Reads the token's {@code roles} claim (["USER", "ORGANIZER"]) and turns it into the
     * {@code ROLE_}-prefixed authorities that {@code hasRole(...)} expects.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            final AuthenticationConfiguration authenticationConfiguration) throws Exception {

        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
