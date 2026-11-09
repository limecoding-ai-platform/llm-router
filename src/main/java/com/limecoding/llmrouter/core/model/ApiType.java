package com.limecoding.llmrouter.core.model;

/** Client-facing protocol, carried through routing independently of provider capabilities. */
public enum ApiType {
    CHAT_COMPLETIONS,
    RESPONSES
}
