package com.flowstate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFileApiKeyProviderTest {

    @Test
    void validatesKeysLoadedFromApiKeysArray() throws Exception {
        JsonFileApiKeyProvider provider = new JsonFileApiKeyProvider(new ObjectMapper());
        ReflectionTestUtils.setField(provider, "apiKeyResource", new ByteArrayResource(
                "{\"apiKeys\":[\"FLOW1234\",\"ABCD1234\"]}".getBytes(StandardCharsets.UTF_8)));

        provider.loadApiKey();

        assertThat(provider.isValid("FLOW1234")).isTrue();
        assertThat(provider.isValid("ABCD1234")).isTrue();
        assertThat(provider.isValid("INVALID1")).isFalse();
        assertThat(provider.isValid(null)).isFalse();
    }

    @Test
    void validatesLegacySingleApiKeyFormat() throws Exception {
        JsonFileApiKeyProvider provider = new JsonFileApiKeyProvider(new ObjectMapper());
        ReflectionTestUtils.setField(provider, "apiKeyResource", new ByteArrayResource(
                "{\"apiKey\":\"LEGACY12\"}".getBytes(StandardCharsets.UTF_8)));

        provider.loadApiKey();

        assertThat(provider.isValid("LEGACY12")).isTrue();
        assertThat(provider.isValid("FLOW1234")).isFalse();
    }
}
