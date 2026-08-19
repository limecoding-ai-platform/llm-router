package com.limecoding.llmrouter.provider.openai;

import com.limecoding.llmrouter.core.model.ChatRequest;
import com.limecoding.llmrouter.core.model.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OpenAiAdapterTest {

    @Autowired
    OpenAiAdapter adapter;

    @Test
    void openAI_API를_호출한다() {
        var response = adapter.chat(new ChatRequest(
                "gpt-4o-mini",
                List.of(Message.user("안녕")),
                5,
                null));

        System.out.println(response);
        assertThat(response.message().content()).isNotBlank().isNotBlank();
    }

}