package com.flowstate.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowstate.dto.memory.ForgetRequest;
import com.flowstate.dto.memory.MemoryResponse;
import com.flowstate.dto.memory.RecallRequest;
import com.flowstate.dto.memory.StoreRequest;
import com.flowstate.service.memory.MemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Workflow Memory API Controller.
 * 
 *   POST   /v1/store   {key, value, ttl}  — remember something
 *   GET    /v1/recall?key=xxx             — get it back
 *   DELETE /v1/forget?key=xxx             — delete it
 *   GET    /v1/health                     — service alive check (no auth)
 *
 * All responses return HTTP 200 with success:true/false in body.
 * Zapier treats non-200 as failure — so we never throw.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MemoryController {

    private static final Pattern KEY_NAMESPACE_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");
    private static final int MAX_KEY_LENGTH = 32;
    private static final int MAX_NAMESPACE_LENGTH = 32;
    private static final int MAX_VALUE_BYTES = 1024;

    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    /**
     * POST /v1/store
     * Store a key-value pair with optional TTL and namespace.
     */
    @PostMapping("/store")
    public ResponseEntity<MemoryResponse> store(
            @Valid @RequestBody StoreRequest request,
            @RequestAttribute("workspaceId") String workspaceId) {

        try {
            byte[] valueBytes = objectMapper.writeValueAsBytes(request.getValue());
            if (valueBytes.length > MAX_VALUE_BYTES) {
                return ResponseEntity.ok(validationError("Value must not exceed 1024 bytes when serialized to JSON"));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize store value for key={} ws={}", request.getKey(), workspaceId, e);
            return ResponseEntity.ok(validationError("Value must be JSON serializable"));
        }

        log.info("STORE key={} ns={} ws={}", request.getKey(), request.getNamespace(), workspaceId);
        MemoryResponse response = memoryService.store(request, workspaceId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/recall?key=xxx&namespace=yyy
     * Recall a stored value by key.
     */
    @GetMapping("/recall")
    public ResponseEntity<MemoryResponse> recall(
            @RequestParam String key,
            @RequestParam(required = false) String namespace,
            @RequestAttribute("workspaceId") String workspaceId) {

        String validationError = validateKeyAndNamespace(key, namespace);
        if (validationError != null) {
            return ResponseEntity.ok(validationError(validationError));
        }

        log.info("RECALL key={} ns={} ws={}", key, namespace, workspaceId);
        RecallRequest request = RecallRequest.builder().key(key).namespace(namespace).build();
        MemoryResponse response = memoryService.recall(request, workspaceId);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /v1/forget?key=xxx&namespace=yyy
     * Forget (delete) a stored value.
     */
    @DeleteMapping("/forget")
    public ResponseEntity<MemoryResponse> forget(
            @RequestParam String key,
            @RequestParam(required = false) String namespace,
            @RequestAttribute("workspaceId") String workspaceId) {

        String validationError = validateKeyAndNamespace(key, namespace);
        if (validationError != null) {
            return ResponseEntity.ok(validationError(validationError));
        }

        log.info("FORGET key={} ns={} ws={}", key, namespace, workspaceId);
        ForgetRequest request = ForgetRequest.builder().key(key).namespace(namespace).build();
        MemoryResponse response = memoryService.forget(request, workspaceId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/health — service alive check (no auth required).
     */
    @GetMapping("/health")
    public ResponseEntity<MemoryResponse> health() {
        return ResponseEntity.ok(MemoryResponse.builder()
                .success(true)
                .message("ok")
                .build());
    }

    private String validateKeyAndNamespace(String key, String namespace) {
        if (key == null || key.isBlank()) {
            return "Key is required";
        }
        if (key.length() > MAX_KEY_LENGTH) {
            return "Key must be between 1 and 32 characters";
        }
        if (!KEY_NAMESPACE_PATTERN.matcher(key).matches()) {
            return "Key must match pattern [a-zA-Z0-9_-]+";
        }

        if (namespace != null) {
            if (namespace.isBlank()) {
                return "Namespace must match pattern [a-zA-Z0-9_-]+";
            }
            if (namespace.length() > MAX_NAMESPACE_LENGTH) {
                return "Namespace must not exceed 32 characters";
            }
            if (!KEY_NAMESPACE_PATTERN.matcher(namespace).matches()) {
                return "Namespace must match pattern [a-zA-Z0-9_-]+";
            }
        }

        return null;
    }

    private MemoryResponse validationError(String message) {
        return MemoryResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
