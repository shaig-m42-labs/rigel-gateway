# rigel-gateway
API gateway for routing, authentication forwarding, rate limiting, and service entrypoint management.

**Language:** `Java`
**Stack:** `Spring Cloud Gateway, Redis, rate limiting, JWT validation or auth forwarding.`

**Features:**
```
Route /auth/** -> bellatrix-auth
Route /core/** -> betelgeuse-core
Route /events/** -> alnitak-events if needed
Request correlation id
Rate limiting
CORS
Centralized error response
Forward user headers
Swagger aggregation optional
```
