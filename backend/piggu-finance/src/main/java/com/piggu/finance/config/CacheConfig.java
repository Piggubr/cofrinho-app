package com.piggu.finance.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache local do servico.
 *
 * <p>A cotacao vale por uma hora, o mesmo prazo do CacheService do Apps Script.
 * Como cada instancia mantem o proprio cache, um valor pode ficar ate uma hora
 * diferente entre replicas: aceitavel para uma cotacao de referencia.</p>
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(IntegracoesProperties.class)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager gerenciador = new CaffeineCacheManager("cotacao");
        gerenciador.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(50));
        return gerenciador;
    }
}
