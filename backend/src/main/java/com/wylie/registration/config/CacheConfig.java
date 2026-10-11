package com.wylie.registration.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/** 中间层缓存配置（Caffeine 进程内缓存） */
@Configuration
public class CacheConfig {

    public static final String CATALOG_CACHE = "legacyCatalog";

    @Bean
    public CacheManager cacheManager(LegacyProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager(CATALOG_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(props.getCacheTtlSeconds(), TimeUnit.SECONDS));
        return manager;
    }
}