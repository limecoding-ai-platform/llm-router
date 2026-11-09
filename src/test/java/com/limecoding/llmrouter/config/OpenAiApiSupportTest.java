package com.limecoding.llmrouter.config;

import com.limecoding.llmrouter.config.properties.OpenAiProperties;
import com.limecoding.llmrouter.core.model.ApiType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiApiSupportTest {
    @Test void bindsBothApisAndDefaultsLegacyModelsToChat() {
        var source = new MapConfigurationPropertySource(Map.of("model.id", "test", "model.max-tokens-param", "max_tokens",
                "model.supported-apis[0]", "chat_completions", "model.supported-apis[1]", "responses"));
        var both = new Binder(source).bind("model", OpenAiProperties.Model.class).get();
        assertEquals(Set.of(ApiType.CHAT_COMPLETIONS, ApiType.RESPONSES), both.supportedApis());
        var legacy = new Binder(new MapConfigurationPropertySource(Map.of("model.id", "test", "model.max-tokens-param", "max_tokens")))
                .bind("model", OpenAiProperties.Model.class).get();
        assertEquals(Set.of(ApiType.CHAT_COMPLETIONS), legacy.supportedApis());
    }

    @Test void responsesOnlyNeedsNoChatTokenDialectButChatDoes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var responses = new Binder(new MapConfigurationPropertySource(Map.of("model.id", "test", "model.supported-apis[0]", "responses")))
                    .bind("model", OpenAiProperties.Model.class).get();
            assertTrue(validator.validate(responses).isEmpty());
            var chat = new Binder(new MapConfigurationPropertySource(Map.of("model.id", "test")))
                    .bind("model", OpenAiProperties.Model.class).get();
            assertFalse(validator.validate(chat).isEmpty());
        }
    }
}
