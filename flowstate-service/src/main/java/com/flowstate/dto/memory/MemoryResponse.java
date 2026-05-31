package com.flowstate.dto.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryResponse {

    private boolean success;
    private String key;
    private Object value;
    private String namespace;
    private Instant createdAt;
    private Instant expiresAt;
    private Long ttlRemainingSeconds;

    // Metadata
    private String message;
}
