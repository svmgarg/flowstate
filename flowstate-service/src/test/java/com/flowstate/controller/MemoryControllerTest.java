package com.flowstate.controller;

import com.flowstate.dto.memory.MemoryResponse;
import com.flowstate.exception.GlobalExceptionHandler;
import com.flowstate.service.memory.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryService memoryService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Test
    void storeReturnsSuccessForValidRequest() throws Exception {
        when(memoryService.store(any(), eq("FLOW1234"))).thenReturn(MemoryResponse.builder()
                .success(true)
                .key("order1")
                .namespace("orders")
                .message("Stored successfully")
                .build());

        mockMvc.perform(post("/v1/store")
                        .requestAttr("workspaceId", "FLOW1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "order1",
                                  "namespace": "orders",
                                  "value": {"status": "done"},
                                  "ttlSeconds": 60
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.key").value("order1"))
                .andExpect(jsonPath("$.namespace").value("orders"))
                .andExpect(jsonPath("$.message").value("Stored successfully"));

        verify(memoryService).store(any(), eq("FLOW1234"));
    }

    @Test
    void storeReturnsValidationErrorForKeyLongerThan32Characters() throws Exception {
        mockMvc.perform(post("/v1/store")
                        .requestAttr("workspaceId", "FLOW1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "123456789012345678901234567890123",
                                  "namespace": "orders",
                                  "value": "ok"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.value.key").value("Key must be between 1 and 32 characters"));

        verify(memoryService, never()).store(any(), any());
    }

    @Test
    void storeReturnsValidationErrorForNamespaceLongerThan32Characters() throws Exception {
        mockMvc.perform(post("/v1/store")
                        .requestAttr("workspaceId", "FLOW1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "order1",
                                  "namespace": "123456789012345678901234567890123",
                                  "value": "ok"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.value.namespace").value("Namespace must not exceed 32 characters"));

        verify(memoryService, never()).store(any(), any());
    }

    @Test
    void storeReturnsValidationErrorWhenSerializedValueExceeds1024Bytes() throws Exception {
        String largeValue = "a".repeat(1025);

        mockMvc.perform(post("/v1/store")
                        .requestAttr("workspaceId", "FLOW1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "order1",
                                  "namespace": "orders",
                                  "value": "%s"
                                }
                                """.formatted(largeValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Value must not exceed 1024 bytes when serialized to JSON"));

        verify(memoryService, never()).store(any(), any());
    }

    @Test
    void recallReturnsStoredValueWhenFound() throws Exception {
        when(memoryService.recall(any(), eq("FLOW1234"))).thenReturn(MemoryResponse.builder()
                .success(true)
                .key("order1")
                .namespace("orders")
                .value("done")
                .message("Recalled successfully")
                .build());

        mockMvc.perform(get("/v1/recall")
                        .requestAttr("workspaceId", "FLOW1234")
                        .param("key", "order1")
                        .param("namespace", "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.key").value("order1"))
                .andExpect(jsonPath("$.value").value("done"))
                .andExpect(jsonPath("$.message").value("Recalled successfully"));
    }

    @Test
    void recallReturnsFailureWhenNotFound() throws Exception {
        when(memoryService.recall(any(), eq("FLOW1234"))).thenReturn(MemoryResponse.builder()
                .success(false)
                .key("missing")
                .namespace("orders")
                .message("Key not found")
                .build());

        mockMvc.perform(get("/v1/recall")
                        .requestAttr("workspaceId", "FLOW1234")
                        .param("key", "missing")
                        .param("namespace", "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Key not found"));
    }

    @Test
    void forgetReturnsSuccess() throws Exception {
        when(memoryService.forget(any(), eq("FLOW1234"))).thenReturn(MemoryResponse.builder()
                .success(true)
                .key("order1")
                .namespace("orders")
                .message("Forgotten successfully")
                .build());

        mockMvc.perform(delete("/v1/forget")
                        .requestAttr("workspaceId", "FLOW1234")
                        .param("key", "order1")
                        .param("namespace", "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Forgotten successfully"));
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"));
    }
}
