package com.limecoding.llmrouter.core.model;

import java.util.Locale;

public record ModelRef(String provider, String model, String original) {

    public static ModelRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("ModelRef cannot be null or empty");
        }

        String trimmed = raw.trim();
        int i = trimmed.indexOf('/');

        if (i <= 0 || i == trimmed.length() - 1) {
            throw new IllegalArgumentException("model must be in 'provider/model' form, but was: " + raw);
        }

        return new ModelRef(
                trimmed.substring(0, i).toLowerCase(Locale.ROOT),
                trimmed.substring(i + 1),
                raw);
    }
}
