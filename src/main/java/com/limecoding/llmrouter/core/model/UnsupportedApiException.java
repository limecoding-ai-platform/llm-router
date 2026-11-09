package com.limecoding.llmrouter.core.model;

public class UnsupportedApiException extends RuntimeException {
    public UnsupportedApiException(String model, ApiType api) {
        super("Model '" + model + "' is not configured to support " + api.name().toLowerCase(java.util.Locale.ROOT));
    }
}
