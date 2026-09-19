package com.limecoding.llmrouter.core.model;

public class UnKnownProviderException extends RuntimeException {
    private String id;

    public UnKnownProviderException(String id) {
        super("Provider with id " + id + " not found");
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
