package com.flowstate.service.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowstate.dto.memory.ForgetRequest;
import com.flowstate.dto.memory.MemoryResponse;
import com.flowstate.dto.memory.RecallRequest;
import com.flowstate.dto.memory.StoreRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMemoryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisMemoryService redisMemoryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisMemoryService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(redisMemoryService, "defaultTtlSeconds", 86400L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storePersistsValueUsingWorkspaceNamespaceAndKeyPattern() throws Exception {
        StoreRequest request = StoreRequest.builder()
                .key("order1")
                .namespace("orders")
                .value(Map.of("status", "done"))
                .ttlSeconds(60L)
                .build();

        MemoryResponse response = redisMemoryService.store(request, "FLOW1234");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("FLOW1234:orders:order1"), jsonCaptor.capture(), eq(Duration.ofSeconds(60)));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = new ObjectMapper().readValue(jsonCaptor.getValue(), Map.class);
        assertThat(envelope.get("v")).isEqualTo(Map.of("status", "done"));
        assertThat(envelope.get("ns")).isEqualTo("orders");
        assertThat(envelope).containsKey("cat");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTtlRemainingSeconds()).isEqualTo(60L);
        assertThat(response.getNamespace()).isEqualTo("orders");
    }

    @Test
    void recallReturnsStoredValueAndTtlWhenKeyExists() {
        String redisKey = "FLOW1234:orders:order1";
        when(valueOperations.get(redisKey)).thenReturn("{\"v\":\"done\",\"ns\":\"orders\",\"cat\":1700000000000}");
        when(redisTemplate.getExpire(redisKey)).thenReturn(42L);

        MemoryResponse response = redisMemoryService.recall(RecallRequest.builder()
                .key("order1")
                .namespace("orders")
                .build(), "FLOW1234");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getKey()).isEqualTo("order1");
        assertThat(response.getNamespace()).isEqualTo("orders");
        assertThat(response.getValue()).isEqualTo("done");
        assertThat(response.getTtlRemainingSeconds()).isEqualTo(42L);
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    void recallReturnsFailureWhenKeyDoesNotExist() {
        when(valueOperations.get("FLOW1234:orders:missing")).thenReturn(null);

        MemoryResponse response = redisMemoryService.recall(RecallRequest.builder()
                .key("missing")
                .namespace("orders")
                .build(), "FLOW1234");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Key not found");
    }

    @Test
    void forgetDeletesExistingKey() {
        when(redisTemplate.delete("FLOW1234:orders:order1")).thenReturn(true);

        MemoryResponse response = redisMemoryService.forget(ForgetRequest.builder()
                .key("order1")
                .namespace("orders")
                .build(), "FLOW1234");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Forgotten successfully");
    }

    @Test
    void forgetReturnsFailureWhenKeyDoesNotExist() {
        when(redisTemplate.delete("FLOW1234:orders:missing")).thenReturn(false);

        MemoryResponse response = redisMemoryService.forget(ForgetRequest.builder()
                .key("missing")
                .namespace("orders")
                .build(), "FLOW1234");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Key not found (already forgotten or never existed)");
    }
}
