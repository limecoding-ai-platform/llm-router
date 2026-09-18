package com.limecoding.llmrouter.core.model;

public class InvalidModelRefException extends RuntimeException {
    private final String requestedModel;

    public InvalidModelRefException(String requestedModel) {
        super("Model must be in 'provider/model' from, but was: " + requestedModel);
        this.requestedModel = requestedModel;
    }

    public String requestedModel() {
        return requestedModel;
    }

}
