package com.flowstate.config;

import com.flowstate.dto.memory.MemoryResponse;
import com.flowstate.service.memory.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.server.port=-1")
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryService memoryService;

    @Test
    void publicHealthEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"));
    }

    @Test
    void protectedEndpointReturnsSuccessFalseWhenAuthenticationIsMissing() throws Exception {
        mockMvc.perform(get("/v1/recall").param("key", "order1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized - Invalid or missing API key"));
    }

    @Test
    void protectedEndpointAllowsValidApiKey() throws Exception {
        when(memoryService.recall(any(), eq("FLOW1234"))).thenReturn(MemoryResponse.builder()
                .success(true)
                .key("order1")
                .value("done")
                .message("Recalled successfully")
                .build());

        mockMvc.perform(get("/v1/recall")
                        .header("X-API-KEY", "FLOW1234")
                        .param("key", "order1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.key").value("order1"))
                .andExpect(jsonPath("$.value").value("done"));

        verify(memoryService).recall(any(), eq("FLOW1234"));
    }
}
