package com.limecoding.llmrouter.api;

import com.limecoding.llmrouter.api.dto.model.ModelInfo;
import com.limecoding.llmrouter.api.dto.model.ModelResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/v1/models")
@RestController
public class ModelController {

    @GetMapping
    public ModelResponse getModel() {
        return new ModelResponse(
                "list",
                List.of(
                        new ModelInfo(
                                "openai/gpt-3.5-turbo",
                                "model",
                                1677610602L,
                                "openai",
                                "2026-10-23"),
                        new ModelInfo(
                                "openai/gpt-5.6-terra",
                                "model",
                                1782228459L,
                                "system",
                                null)
                )
        );
    }
}
