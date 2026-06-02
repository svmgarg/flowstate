package com.flowstate.service.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowstate.dto.memory.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Redis implementation of MemoryService.
 * Uses Redis hashes for metadata and strings for values.
 * Suitable for distributed/production deployments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMemoryService implements MemoryService {

    @Value("${memory.default-ttl-seconds:86400}")
    private long defaultTtlSeconds;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public MemoryResponse store(StoreRequest request, String workspaceId) {
        String redisKey = buildRedisKey(workspaceId, request.getNamespace(), request.getKey());
        long ttl = request.getTtlSeconds() != null ? request.getTtlSeconds() : defaultTtlSeconds;
        Instant now = Instant.now();
        Instant expiresAt = ttl > 0 ? now.plusSeconds(ttl) : null;

        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("v", request.getValue());
            envelope.put("ns", request.getNamespace());
            envelope.put("cat", now.toEpochMilli());

            String jsonValue = objectMapper.writeValueAsString(envelope);

            if (ttl > 0) {
                redisTemplate.opsForValue().set(redisKey, jsonValue, Duration.ofSeconds(ttl));
            } else {
                redisTemplate.opsForValue().set(redisKey, jsonValue);
            }

            log.debug("Redis stored key: {} in workspace: {}", request.getKey(), workspaceId);

            return MemoryResponse.builder()
                    .success(true)
                    .key(request.getKey())
                    .namespace(request.getNamespace())
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .ttlRemainingSeconds(ttl > 0 ? ttl : null)
                    .message("Stored successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to store key: {} in Redis", request.getKey(), e);
            return MemoryResponse.builder()
                    .success(false)
                    .key(request.getKey())
                    .message("Storage unavailable, please retry")
                    .build();
        }
    }

    @Override
    public MemoryResponse recall(RecallRequest request, String workspaceId) {
        String redisKey = buildRedisKey(workspaceId, request.getNamespace(), request.getKey());

        try {
            String serialized = redisTemplate.opsForValue().get(redisKey);

            if (serialized == null) {
                return MemoryResponse.builder()
                        .success(false)
                        .key(request.getKey())
                        .namespace(request.getNamespace())
                        .message("Key not found")
                        .build();
            }

            // Deserialize envelope
            Map<String, Object> envelope = objectMapper.readValue(serialized, Map.class);
            Object value = envelope.get("v");
            Long createdAtMillis = envelope.get("cat") != null ? ((Number) envelope.get("cat")).longValue() : null;
            Instant createdAt = createdAtMillis != null ? Instant.ofEpochMilli(createdAtMillis) : null;

            // Get TTL
            Long ttlRemaining = redisTemplate.getExpire(redisKey);
            Instant expiresAt = (ttlRemaining != null && ttlRemaining > 0)
                    ? Instant.now().plusSeconds(ttlRemaining) : null;

            return MemoryResponse.builder()
                    .success(true)
                    .key(request.getKey())
                    .value(value)
                    .namespace(request.getNamespace())
                    .createdAt(createdAt)
                    .expiresAt(expiresAt)
                    .ttlRemainingSeconds(ttlRemaining != null && ttlRemaining > 0 ? ttlRemaining : null)
                    .message("Recalled successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to recall key: {} from Redis", request.getKey(), e);
            return MemoryResponse.builder()
                    .success(false)
                    .key(request.getKey())
                    .message("Internal error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public MemoryResponse forget(ForgetRequest request, String workspaceId) {
        String redisKey = buildRedisKey(workspaceId, request.getNamespace(), request.getKey());

        try {
            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Redis forgot key: {} in workspace: {}", request.getKey(), workspaceId);
                return MemoryResponse.builder()
                        .success(true)
                        .key(request.getKey())
                        .namespace(request.getNamespace())
                        .message("Forgotten successfully")
                        .build();
            }

            return MemoryResponse.builder()
                    .success(false)
                    .key(request.getKey())
                    .namespace(request.getNamespace())
                    .message("Key not found (already forgotten or never existed)")
                    .build();

        } catch (Exception e) {
            log.error("Failed to forget key: {} from Redis", request.getKey(), e);
            return MemoryResponse.builder()
                    .success(false)
                    .key(request.getKey())
                    .message("Storage unavailable, please retry")
                    .build();
        }
    }

    @Override
    public MemoryResponse storeIfAbsent(StoreRequest request, String workspaceId) {
        String redisKey = buildRedisKey(workspaceId, request.getNamespace(), request.getKey());
        long ttl = request.getTtlSeconds() != null ? request.getTtlSeconds() : defaultTtlSeconds;
        Instant now = Instant.now();

        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("v", request.getValue());
            envelope.put("ns", request.getNamespace());
            envelope.put("cat", now.toEpochMilli());

            String jsonValue = objectMapper.writeValueAsString(envelope);

            // Atomic SETNX — only stores if key doesn't exist
            Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(
                    redisKey, jsonValue, Duration.ofSeconds(ttl > 0 ? ttl : defaultTtlSeconds));

            if (Boolean.TRUE.equals(wasSet)) {
                log.debug("SETNX stored key: {} in workspace: {}", request.getKey(), workspaceId);
                return MemoryResponse.builder()
                        .success(true)
                        .key(request.getKey())
                        .namespace(request.getNamespace())
                        .createdAt(now)
                        .expiresAt(ttl > 0 ? now.plusSeconds(ttl) : null)
                        .ttlRemainingSeconds(ttl > 0 ? ttl : null)
                        .message("Stored (new key)")
                        .build();
            }

            // Key already existed
            return MemoryResponse.builder()
                    .success(true)
                    .key(request.getKey())
                    .namespace(request.getNamespace())
                    .message("Already exists (duplicate)")
                    .build();

        } catch (Exception e) {
            log.error("Failed to store-if-absent key: {} in Redis", request.getKey(), e);
            return MemoryResponse.builder()
                    .success(false)
                    .key(request.getKey())
                    .message("Storage unavailable, please retry")
                    .build();
        }
    }

    /**
     * Builds Redis key: {workspaceId}:{namespace}:{key}
     */
    private String buildRedisKey(String workspaceId, String namespace, String key) {
        String ns = (namespace != null && !namespace.isEmpty()) ? namespace : "_default";
        return workspaceId + ":" + ns + ":" + key;
    }
}
