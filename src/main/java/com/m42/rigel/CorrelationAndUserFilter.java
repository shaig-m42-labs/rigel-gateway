package com.m42.rigel;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
class CorrelationAndUserFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(CorrelationAndUserFilter.class);
    private final WebClient webClient;

    CorrelationAndUserFilter(@Value("${app.auth-service-url:${AUTH_SERVICE_URL:http://localhost:8081}}") String authServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(authServiceUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate().header("X-Correlation-Id", finalCorrelationId);
        exchange.getResponse().getHeaders().set("X-Correlation-Id", finalCorrelationId);

        String path = exchange.getRequest().getPath().value();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (path.startsWith("/auth/") || !path.startsWith("/core/")) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return webClient.get()
                .uri("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("X-Correlation-Id", finalCorrelationId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    JsonNode data = json.path("data");
                    builder.header("X-User-Id", data.path("id").asText(""));
                    builder.header("X-User-Email", data.path("email").asText(""));
                    builder.header("X-User-Roles", data.path("role").asText(""));
                    return chain.filter(exchange.mutate().request(builder.build()).build());
                })
                .onErrorResume(ex -> {
                    log.debug("Could not validate token through auth service: {}", ex.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
