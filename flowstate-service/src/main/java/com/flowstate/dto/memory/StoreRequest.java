package com.flowstate.dto.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoreRequest {

    @NotBlank(message = "Key is required")
    @Size(min = 1, max = 32, message = "Key must be between 1 and 32 characters")
    @Pattern(regexp = "[a-zA-Z0-9_-]+", message = "Key must match pattern [a-zA-Z0-9_-]+")
    private String key;

    /**
     * The value to store. Can be any JSON-compatible object (string, number, object, array).
     */
    @NotNull(message = "Value is required")
    private Object value;

    /**
     * TTL in seconds. Default: 86400 (24 hours). Max: 86400 (24 hours). 0 = no expiry.
     */
    @Min(value = 0, message = "TTL must be 0 (no expiry) or positive")
    @Max(value = 86400, message = "TTL cannot exceed 86400 seconds (24 hours)")
    private Long ttlSeconds;

    /**
     * Optional namespace to group related keys.
     */
    @Size(max = 32, message = "Namespace must not exceed 32 characters")
    @Pattern(regexp = "[a-zA-Z0-9_-]+", message = "Namespace must match pattern [a-zA-Z0-9_-]+")
    private String namespace;
}
