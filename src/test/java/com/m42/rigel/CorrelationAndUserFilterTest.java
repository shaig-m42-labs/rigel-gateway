package com.m42.rigel;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationAndUserFilterTest {
    private HttpServer authServer;

    @AfterEach
    void stopServer() {
        if (authServer != null) {
            authServer.stop(0);
        }
    }

    @Test
    void rejectsCoreRequestWithoutBearerToken() {
        CorrelationAndUserFilter filter = new CorrelationAndUserFilter("http://localhost:1");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/core/organizations").build());
        AtomicBoolean called = new AtomicBoolean(false);

        filter.filter(exchange, chain(called, new AtomicReference<>())).block();

        assertThat(called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void forwardsUserHeadersForValidCoreToken() throws IOException {
        startAuthServer(200, """
                {
                  "success": true,
                  "data": {
                    "id": "user-1",
                    "email": "demo@example.com",
                    "role": "MEMBER"
                  }
                }
                """);

        CorrelationAndUserFilter filter = new CorrelationAndUserFilter("http://localhost:" + authServer.getAddress().getPort());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/core/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .header("X-Correlation-Id", "corr-1")
                        .build());
        AtomicBoolean called = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(called, forwarded)).block();

        assertThat(called).isTrue();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-1");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("demo@example.com");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("MEMBER");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-1");
    }

    @Test
    void rejectsCoreRequestWhenAuthServiceRejectsToken() throws IOException {
        startAuthServer(401, "{\"success\":false}");

        CorrelationAndUserFilter filter = new CorrelationAndUserFilter("http://localhost:" + authServer.getAddress().getPort());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/core/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .build());
        AtomicBoolean called = new AtomicBoolean(false);

        filter.filter(exchange, chain(called, new AtomicReference<>())).block();

        assertThat(called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private GatewayFilterChain chain(AtomicBoolean called, AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            called.set(true);
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private void startAuthServer(int status, String body) throws IOException {
        authServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        authServer.createContext("/auth/me", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        authServer.start();
    }
}
