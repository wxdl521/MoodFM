package com.moodfm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class SpringAIConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Bean
    public RestClient.Builder restClientBuilder() {
        String masked = apiKey.length() > 4 ? "****" + apiKey.substring(apiKey.length() - 4) : "(empty)";
        log.info("=== SpringAIConfig: Creating RestClient.Builder with API key ending: {} ===", masked);
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultHeader("Authorization", "Bearer " + apiKey);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
