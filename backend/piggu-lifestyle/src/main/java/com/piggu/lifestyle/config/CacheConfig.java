package com.piggu.lifestyle.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache das buscas no catalogo de produtos.
 *
 * <p>Seis horas, o mesmo prazo do CacheService no Apps Script. O catalogo muda pouco
 * e a API do OpenFoodFacts pede moderacao no volume de chamadas.</p>
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(IntegracoesProperties.class)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager gerenciador = new CaffeineCacheManager("catalogo");
        gerenciador.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(6))
                .maximumSize(500));
        return gerenciador;
    }
}
