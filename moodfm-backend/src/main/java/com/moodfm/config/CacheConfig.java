package com.moodfm.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager() {
            @Override
            protected Cache<Object, Object> createNativeCaffeineCache(String name) {
                return switch (name) {
                    case "songs", "platformMappings" -> Caffeine.newBuilder()
                            .maximumSize("songs".equals(name) ? 50_000 : 5_000)
                            .expireAfterWrite(1, TimeUnit.HOURS)
                            .build();
                    default -> // "users" and others: 10 min TTL
                            Caffeine.newBuilder()
                            .maximumSize(10_000)
                            .expireAfterWrite(10, TimeUnit.MINUTES)
                            .build();
                };
            }
        };
        manager.setAllowNullValues(false);
        return manager;
    }
}
