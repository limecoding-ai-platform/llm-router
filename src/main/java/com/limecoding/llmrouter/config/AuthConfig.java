package com.limecoding.llmrouter.config;

import com.limecoding.llmrouter.api.auth.ApiKeyAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AuthConfig {

    @Bean
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
            RouterProperties props,
            ObjectMapper objectMapper
    ) {
        var filter = new ApiKeyAuthFilter(props.auth().apiKeys(), objectMapper);
        var registration = new FilterRegistrationBean<>(filter);

        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);  // 인증 실패는 최대한 일찍 끊기

        return registration;
    }
}
