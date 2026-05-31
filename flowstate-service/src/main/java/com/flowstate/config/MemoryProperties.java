package com.flowstate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Typed config for the Workflow Memory service.
 * Maps to `memory.*` in application.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /** Default TTL in seconds. 0 = no expiry. */
    private long defaultTtlSeconds = 86400;
}
