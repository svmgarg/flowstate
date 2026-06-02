package com.flowstate.service.memory;

import com.flowstate.dto.memory.*;

/**
 * Workflow Memory Service — store, recall, forget, health.
 * Implementations: InMemory (dev/test) and Redis (production).
 */
public interface MemoryService {

    /**
     * Store a key-value pair with optional TTL and namespace.
     */
    MemoryResponse store(StoreRequest request, String workspaceId);

    /**
     * Recall (retrieve) a value by key.
     */
    MemoryResponse recall(RecallRequest request, String workspaceId);

    /**
     * Forget (delete) a key.
     */
    MemoryResponse forget(ForgetRequest request, String workspaceId);

    /**
     * Store only if the key does not already exist (atomic via SETNX).
     * Returns success:true with existed:false if stored, existed:true if already present.
     */
    MemoryResponse storeIfAbsent(StoreRequest request, String workspaceId);
}
