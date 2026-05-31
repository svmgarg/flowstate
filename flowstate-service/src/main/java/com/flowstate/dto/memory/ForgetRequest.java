package com.flowstate.dto.memory;

import jakarta.validation.constraints.NotBlank;
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
public class ForgetRequest {

    @NotBlank(message = "Key is required")
    @Size(min = 1, max = 32, message = "Key must be between 1 and 32 characters")
    @Pattern(regexp = "[a-zA-Z0-9_-]+", message = "Key must match pattern [a-zA-Z0-9_-]+")
    private String key;

    /**
     * Optional namespace to scope the deletion.
     */
    @Size(max = 32, message = "Namespace must not exceed 32 characters")
    @Pattern(regexp = "[a-zA-Z0-9_-]+", message = "Namespace must match pattern [a-zA-Z0-9_-]+")
    private String namespace;
}
