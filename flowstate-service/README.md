# Idempotency Service

A low-latency, production-grade idempotency service built with Java Spring Boot that provides atomic check-and-insert operations for idempotency keys. This service ensures that duplicate requests are safely detected and handled across distributed systems.

## Features

- **Atomic Operations**: Uses `ConcurrentHashMap.compute()` (in-memory) or Redis `SET NX` for thread-safe atomic check-and-insert
- **Low Latency**: Sub-millisecond response times for in-memory storage, typically 1-5ms for Redis
- **TTL Support**: Configurable time-to-live for idempotency keys with automatic expiration (max 3600s)
- **Client Namespacing**: Optional client ID to namespace keys for multi-tenant scenarios
- **Dual Storage**: Supports in-memory (default) or Redis storage for single-instance or distributed deployments
- **Auto-cleanup**: Automatic removal of expired entries (scheduled every 60s for in-memory, native expiration for Redis)
- **Thread-safe**: Guaranteed atomic operations under high concurrent load (100+ threads tested)
- **API Key Authentication**: Secured with `X-API-KEY` header; keys loaded from a JSON config file
- **HTTPS by Default**: Runs on port 8443 with TLS, plus HTTP→HTTPS redirect on port 8080
- **Zapier Compatible**: All responses return HTTP 200 with a `resultStatusCode` field for actual status (Zapier treats non-2xx as failures)
- **Startup Validation**: Automatic self-test on boot to verify endpoints are responsive
- **Health Monitoring**: Built-in health endpoint and Spring Actuator integration

## Authentication

All endpoints except `/idempotency/ping` and `/actuator/**` require an API key.

Pass the key in the `X-API-KEY` request header:

```bash
curl -H "X-API-KEY: your-api-key" https://localhost:8443/idempotency/health
```

API keys are loaded at startup from `src/main/resources/apiKey.json`:

```json
// Multiple keys format (preferred)
{ "apiKeys": ["key1", "key2"] }

// Single key format (legacy, still supported)
{ "apiKey": "your-key" }
```

If the key is missing or invalid, the service returns `401 Unauthorized`.

## API Endpoints

### POST /idempotency/check *(authenticated)*

Atomically checks if an idempotency key exists and inserts it if not.

**Request Body:**
```json
{
  "idempotencyKey": "unique-key-123",
  "clientId": "optional-client-id",
  "ttlSeconds": 3600
}
```

**Parameters:**
| Field | Required | Constraints | Description |
|-------|----------|-------------|-------------|
| `idempotencyKey` | Yes | 1–64 characters | Unique identifier for the request |
| `clientId` | No | Max 64 characters | Client identifier for key namespacing |
| `ttlSeconds` | No | 1–3600 (default: 3600) | Time-to-live in seconds |

**Response (200 OK — New Key):**
```json
{
  "idempotencyKey": "unique-key-123",
  "isDuplicate": false,
  "resultStatusCode": 200,
  "createdAt": "2026-01-28T10:30:00Z",
  "expiresAt": "2026-01-28T11:30:00Z",
  "processingTimeNanos": 45000
}
```

**Response (200 OK — Duplicate Key):**
```json
{
  "idempotencyKey": "unique-key-123",
  "isDuplicate": true,
  "resultStatusCode": 200,
  "createdAt": "2026-01-28T10:30:00Z",
  "expiresAt": "2026-01-28T11:30:00Z",
  "processingTimeNanos": 32000
}
```

**Response (200 OK — Validation Error):**

> **Note:** Returns HTTP 200 with `resultStatusCode: 400` for Zapier compatibility.

```json
{
  "resultStatusCode": 400,
  "message": "Validation Failed",
  "validationErrors": {
    "idempotencyKey": "Idempotency key is required"
  }
}
```

### GET /idempotency/health *(authenticated)*

Health check endpoint that returns the service status.

**Response (200 OK):**
```json
{
  "status": "UP",
  "service": "idempotency-service",
  "timestamp": "2026-01-28T10:30:00Z",
  "message": "Service is healthy and operational"
}
```

### GET /idempotency/ping *(public — no auth required)*

Simple liveness probe.

**Response (200 OK):**
```
pong
```

### Actuator Endpoints *(public — no auth required)*

Spring Boot Actuator endpoints for monitoring:
- `GET /actuator/health` — Detailed health information
- `GET /actuator/info` — Application information
- `GET /actuator/metrics` — Application metrics

## Architecture

### Storage Backends

#### In-Memory Storage (Default)
- **Use Case**: Single-instance deployments, testing, development
- **Pros**: Ultra-low latency (<100 microseconds), no external dependencies
- **Cons**: Not suitable for distributed deployments, data lost on restart
- **Thread Safety**: Uses `ConcurrentHashMap.compute()` for atomic check-and-insert
- **Cleanup**: Scheduled task runs every 60 seconds to remove expired entries

#### Redis Storage
- **Use Case**: Distributed deployments, production environments
- **Pros**: Shared across multiple service instances, persistent option available
- **Cons**: Network latency (typically 1-5ms), requires Redis infrastructure
- **Atomic Operations**: Uses Redis `SET NX` (`setIfAbsent`) for atomic check-and-insert
- **TTL**: Leverages Redis native key expiration

### Key Namespacing

Keys are internally namespaced by `clientId` when provided:

| clientId | idempotencyKey | Internal Key |
|----------|---------------|--------------|
| *(empty)* | `txn-123` | `txn-123` |
| `client-a` | `txn-123` | `client-a:txn-123` |
| `client-b` | `txn-123` | `client-b:txn-123` |

Redis keys additionally get a `idempotency:` prefix (e.g., `idempotency:client-a:txn-123`).

### Security Architecture

```
Request → ApiKeyAuthenticationFilter → ApiKeyAuthenticationProvider → ApiKeyProvider
                                                                          ↓
                                                                  JsonFileApiKeyProvider
                                                                   (reads apiKey.json)
```

- Stateless session management (no cookies/sessions)
- CSRF disabled (API-only service)
- Public endpoints: `/idempotency/ping`, `/actuator/**`
- Protected endpoints: everything else

### Error Handling (Zapier Compatibility)

All responses return **HTTP 200** to avoid breaking integrations like Zapier that treat non-2xx as failures. The actual status is conveyed in the `resultStatusCode` field:

| Scenario | HTTP Status | `resultStatusCode` | `isDuplicate` |
|----------|------------|---------------------|---------------|
| New key | 200 | 200 | `false` |
| Duplicate key | 200 | 200 | `true` |
| Validation error | 200 | 400 | — |
| Internal error | 200 | 500 | — |
| Invalid API key | 401 | — | — |

### Concurrency & Performance

- **Atomic Operations**: Both implementations guarantee that exactly one request wins the "first" status
- **Performance**: In-memory typically achieves <100 microseconds per operation
- **Scalability**: Redis backend scales across multiple service instances
- **Thread Safety**: Tested with 100+ concurrent threads

## Build & Run

### Prerequisites
- Java 17+
- Maven 3.8+ (Maven wrapper included — `./mvnw`)
- (Optional) Redis 6.0+ for distributed deployments

### Build
```bash
cd idempotent-service
./mvnw clean package
```

### Run (In-Memory Storage — Default)
```bash
./mvnw spring-boot:run
```

The service starts on **https://localhost:8443** (HTTPS) with an HTTP→HTTPS redirect on port 8080.

### Run (Redis Storage)
```bash
IDEMPOTENCY_STORAGE=redis REDIS_HOST=localhost ./mvnw spring-boot:run
```

### Run with Docker
```bash
# Build the Docker image
docker build -t idempotency-service:latest .

# Run with in-memory storage
docker run -p 8443:8443 -p 8080:8080 idempotency-service:latest

# Run with Redis storage
docker run -e IDEMPOTENCY_STORAGE=redis -e REDIS_HOST=redis-host \
  -p 8443:8443 -p 8080:8080 idempotency-service:latest
```

### Test
```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=InMemoryIdempotencyServiceTest

# Run with coverage
./mvnw test jacoco:report
```

## Usage Examples

### Basic Usage
```bash
# First request — returns isDuplicate=false (new key)
curl -k -X POST https://localhost:8443/idempotency/check \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: your-api-key" \
  -d '{"idempotencyKey": "payment-abc-123"}'

# Second request with same key — returns isDuplicate=true (duplicate)
curl -k -X POST https://localhost:8443/idempotency/check \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: your-api-key" \
  -d '{"idempotencyKey": "payment-abc-123"}'
```

### With Client ID (Multi-tenant)
```bash
# Client A — key is namespaced as "client-a:payment-123"
curl -k -X POST https://localhost:8443/idempotency/check \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: your-api-key" \
  -d '{"idempotencyKey": "payment-123", "clientId": "client-a"}'

# Client B with same key — treated as different key ("client-b:payment-123")
curl -k -X POST https://localhost:8443/idempotency/check \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: your-api-key" \
  -d '{"idempotencyKey": "payment-123", "clientId": "client-b"}'
```

### With Custom TTL
```bash
curl -k -X POST https://localhost:8443/idempotency/check \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: your-api-key" \
  -d '{"idempotencyKey": "payment-123", "ttlSeconds": 300}'
```

### Health Check
```bash
curl -k -H "X-API-KEY: your-api-key" https://localhost:8443/idempotency/health
```

### Ping (No Auth)
```bash
curl -k https://localhost:8443/idempotency/ping
```

## Configuration

### Application Properties (`application.yml`)

| Property | Default | Env Variable | Description |
|----------|---------|-------------|-------------|
| `server.port` | `8443` | `SERVER_PORT` | HTTPS server port |
| `server.ssl.enabled` | `true` | — | Enable/disable TLS |
| `server.ssl.key-store` | `file:./keystore.p12` | `SERVER_SSL_KEYSTORE` | Path to PKCS12 keystore |
| `server.ssl.key-store-password` | — | `SERVER_SSL_KEYSTORE_PASSWORD` | Keystore password |
| `idempotency.storage` | `memory` | `IDEMPOTENCY_STORAGE` | Storage backend: `memory` or `redis` |
| `idempotency.default-ttl-seconds` | `3600` | — | Default TTL for keys (seconds) |
| `spring.data.redis.host` | `localhost` | `REDIS_HOST` | Redis server host |
| `spring.data.redis.port` | `6379` | `REDIS_PORT` | Redis server port |
| `spring.data.redis.timeout` | `2000ms` | — | Redis connection timeout |

### Tomcat Tuning

| Property | Default | Description |
|----------|---------|-------------|
| `server.tomcat.threads.max` | `200` | Max worker threads |
| `server.tomcat.threads.min-spare` | `20` | Min idle threads |
| `server.tomcat.accept-count` | `100` | Connection queue size |
| `server.tomcat.max-connections` | `10000` | Max concurrent connections |

### Redis Connection Pool (Lettuce)

| Property | Default | Description |
|----------|---------|-------------|
| `spring.data.redis.lettuce.pool.max-active` | `50` | Max active connections |
| `spring.data.redis.lettuce.pool.max-idle` | `20` | Max idle connections |
| `spring.data.redis.lettuce.pool.min-idle` | `5` | Min idle connections |
| `spring.data.redis.lettuce.pool.max-wait` | `1000ms` | Max wait for connection |

## Performance Characteristics

| Metric | In-Memory | Redis |
|--------|-----------|-------|
| **Latency** | 50–100 μs | 1–5 ms |
| **Throughput** | ~10,000+ ops/s | ~1,000+ ops/s |
| **Concurrency** | 100+ threads tested | Unlimited instances |
| **Memory per entry** | ~500 bytes | Depends on Redis config |
| **Persistence** | None (lost on restart) | Optional (RDB/AOF) |

## Testing

The service includes comprehensive test coverage:

### Test Suites

| Test Class | Scope | Redis Required |
|------------|-------|---------------|
| `InMemoryIdempotencyServiceTest` | In-memory service: new/dup detection, TTL, concurrency, performance | No |
| `InMemoryIdempotencyServiceEdgeCaseTest` | Edge cases for in-memory service | No |
| `RedisIdempotencyServiceTest` | Redis service integration tests | Yes |
| `IdempotencyControllerTest` | REST endpoint validation | No |
| `IdempotencyControllerEdgeCaseTest` | Controller edge cases | No |
| `GlobalExceptionHandlerTest` | Exception handling and error response format | No |
| `IdempotencyRequestValidationTest` | DTO validation constraints | No |
| `ApiKeyAuthenticationTest` | API key auth filter and provider | No |
| `ApiKeyAuthenticationProviderEdgeCaseTest` | Auth provider edge cases | No |
| `JsonFileApiKeyProviderTest` | API key loading from JSON | No |

### Running Tests
```bash
# All tests
./mvnw test

# Only in-memory tests (no Redis required)
./mvnw test -Dtest="InMemoryIdempotencyServiceTest,IdempotencyControllerTest,GlobalExceptionHandlerTest,ApiKeyAuthenticationTest"

# Redis integration tests only (requires Redis)
./mvnw test -Dtest=RedisIdempotencyServiceTest
```

## Deployment Recommendations

### Single Instance (In-Memory)
- Use default in-memory storage
- Suitable for low-traffic services or testing
- Data is not persisted across restarts

### Distributed (Redis)
- Set `IDEMPOTENCY_STORAGE=redis`
- Configure Redis with proper replication/clustering
- Monitor Redis memory usage
- Consider Redis persistence options (RDB/AOF)

### High Availability
1. Use Redis with master-replica setup
2. Configure multiple service instances behind a load balancer
3. Monitor latency and error rates via Actuator metrics
4. Set up health check probes using `/idempotency/ping` (no auth needed)

### HTTPS / TLS

The service ships with a self-signed PKCS12 keystore (`keystore.p12`) for development.
For production, replace it with a proper certificate:

```bash
# Generate a new keystore (example)
keytool -genkeypair -alias idempotent-service -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 365
```

## Troubleshooting

### 401 Unauthorized
- Verify you're sending `X-API-KEY` header with a valid key
- Check that `src/main/resources/apiKey.json` contains the key
- Check startup logs for "Loaded N API keys from resource"

### High Latency with Redis
- Check Redis server load and network latency
- Verify Lettuce connection pool is not exhausted (increase `max-active`)
- Consider Redis Cluster for high throughput

### Memory Growth (In-Memory)
- Verify TTL is set appropriately (max 3600s enforced)
- Check logs for "Cleaned up N expired idempotency entries" every 60s
- Monitor store size via application logs

### Duplicate Key Conflicts
- Ensure idempotency keys are truly unique per operation
- Use `clientId` for multi-tenant scenarios
- Verify TTL doesn't expire prematurely for long-running operations

## Monitoring & Metrics

```bash
# Actuator health (no auth required)
curl -k https://localhost:8443/actuator/health

# Actuator metrics (no auth required)
curl -k https://localhost:8443/actuator/metrics

# Application health (requires auth)
curl -k -H "X-API-KEY: your-api-key" https://localhost:8443/idempotency/health
```

Key metrics to monitor:
- Request count and response times (p50, p99)
- Error rates by `resultStatusCode`
- Redis connection pool utilization
- In-memory store size (logged periodically)

## Project Structure

```
src/main/java/com/idempotent/
├── IdempotencyServiceApplication.java    # Main application entry point
├── config/
│   └── HttpsRedirectConfig.java          # HTTP→HTTPS redirect (port 8080→8443)
├── controller/
│   └── IdempotencyController.java        # REST endpoints (/check, /health, /ping)
├── dto/
│   ├── HealthResponse.java               # Health check response DTO
│   ├── IdempotencyRequest.java           # Request DTO with validation
│   └── IdempotencyResponse.java          # Response DTO (includes Zapier fields)
├── exception/
│   └── GlobalExceptionHandler.java       # Centralized error handling (returns 200)
├── model/
│   └── IdempotencyRecord.java            # Domain model for stored records
├── security/
│   ├── ApiKeyAuthenticationFilter.java   # Extracts X-API-KEY from request
│   ├── ApiKeyAuthenticationProvider.java  # Validates API key against provider
│   ├── ApiKeyAuthenticationToken.java     # Spring Security auth token
│   └── SecurityConfig.java               # Security filter chain configuration
├── service/
│   ├── ApiKeyProvider.java               # Interface for API key validation
│   ├── IdempotencyService.java           # Service interface
│   ├── InMemoryIdempotencyService.java   # ConcurrentHashMap implementation
│   ├── JsonFileApiKeyProvider.java       # Loads keys from apiKey.json
│   └── RedisIdempotencyService.java      # Redis implementation
└── startup/
    └── StartupValidator.java             # Self-test on boot

src/test/java/com/idempotent/
├── controller/
│   ├── IdempotencyControllerTest.java
│   └── IdempotencyControllerEdgeCaseTest.java
├── dto/
│   └── IdempotencyRequestValidationTest.java
├── exception/
│   └── GlobalExceptionHandlerTest.java
├── security/
│   ├── ApiKeyAuthenticationTest.java
│   └── ApiKeyAuthenticationProviderEdgeCaseTest.java
└── service/
    ├── InMemoryIdempotencyServiceTest.java
    ├── InMemoryIdempotencyServiceEdgeCaseTest.java
    ├── JsonFileApiKeyProviderTest.java
    └── RedisIdempotencyServiceTest.java
```

## Code Quality

- **Test Coverage**: Comprehensive unit, integration, and edge-case tests across all layers
- **Thread Safety**: Atomic operations tested with 100+ concurrent threads
- **Input Validation**: Jakarta Bean Validation with meaningful error messages
- **Error Handling**: Centralized `@RestControllerAdvice` with Zapier-compatible responses
- **Security**: API key authentication with pluggable provider interface
- **Logging**: SLF4J with Lombok `@Slf4j` at INFO and DEBUG levels
- **Code Style**: Lombok for boilerplate reduction, Builder pattern for DTOs

## License
MIT

## Contributing
Please submit issues and pull requests to the main repository.
