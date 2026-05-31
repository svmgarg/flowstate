package com.flowstate.service;

public interface ApiKeyProvider {
    boolean isValid(String apiKey);
}
