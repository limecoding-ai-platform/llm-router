package com.limecoding.llmrouter.core.model;

public record Message(Role role, String content) {

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }
}
