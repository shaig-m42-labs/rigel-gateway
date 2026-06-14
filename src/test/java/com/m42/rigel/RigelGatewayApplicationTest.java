package com.m42.rigel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.data.redis.host=localhost",
        "spring.cloud.gateway.server.webflux.routes[0].id=test",
        "spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:8081",
        "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/test/**"
})
class RigelGatewayApplicationTest {
    @Test
    void contextLoads() {
    }
}
