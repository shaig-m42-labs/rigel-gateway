# rigel-gateway

Public API gateway for Orion Platform V1.

## Routes

- `/auth/**` -> `bellatrix-auth`
- `/core/**` -> `betelgeuse-core` with the `/core` prefix stripped

The gateway adds `X-Correlation-Id`, applies basic Redis-backed rate limiting,
and forwards authenticated user headers when `/auth/me` validates a bearer
token.
