package com.tickify.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

/**
 * Thin HTTP helper for the end-to-end tests: register, log in, and make authenticated calls
 * without repeating header plumbing in every test.
 */
public class ApiClient {

    private final TestRestTemplate rest;
    private String accessToken;

    public ApiClient(TestRestTemplate rest) {
        this.rest = rest;
    }

    public String accessToken() {
        return accessToken;
    }

    /** Register an account with the given roles and log in as it. */
    public ApiClient registerAndLogin(String email, String password, Set<String> roles) {
        ResponseEntity<JsonNode> registration = post("/api/v1/auth/register",
                Map.of("email", email, "password", password, "requestedRoles", roles));

        if (!registration.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Registration failed for " + email + ": " + registration.getBody());
        }

        return login(email, password);
    }

    public ApiClient login(String email, String password) {
        ResponseEntity<JsonNode> response = post("/api/v1/auth/login",
                Map.of("email", email, "password", password));

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Login failed for " + email + ": " + response.getBody());
        }

        this.accessToken = response.getBody().get("accessToken").asText();
        return this;
    }

    public ResponseEntity<JsonNode> get(String path) {
        return exchange(HttpMethod.GET, path, null);
    }

    public ResponseEntity<JsonNode> post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body);
    }

    public ResponseEntity<JsonNode> exchange(HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers()), JsonNode.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }
}
