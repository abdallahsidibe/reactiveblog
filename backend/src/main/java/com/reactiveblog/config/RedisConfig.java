package com.reactiveblog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactiveblog.dto.ArticleResponseDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/*
 * Configuration Redis réactive.
 *
 * On déclare un ReactiveRedisTemplate<String, ArticleResponseDto> :
 *
 *   - Clé (String)  : StringRedisSerializer  → texte UTF-8 lisible dans redis-cli
 *   - Valeur (DTO)  : Jackson2JsonRedisSerializer → JSON (via l'ObjectMapper Spring Boot)
 *
 * Le ReactiveRedisTemplate est l'équivalent réactif de RedisTemplate :
 * toutes ses opérations retournent des Mono<T> / Flux<T>.
 *
 * Lettuce (le client réactif inclus dans spring-boot-starter-data-redis-reactive)
 * ouvre des connexions TCP non-bloquantes via Netty — cohérent avec WebFlux.
 *
 * On réutilise l'ObjectMapper auto-configuré par Spring Boot (JavaTimeModule,
 * désérialisation des Instant…) plutôt que d'en créer un second.
 */
@Configuration
public class RedisConfig {

    @Bean
    ReactiveRedisTemplate<String, ArticleResponseDto> articleRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<ArticleResponseDto> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ArticleResponseDto.class);

        RedisSerializationContext<String, ArticleResponseDto> context =
                RedisSerializationContext.<String, ArticleResponseDto>newSerializationContext(
                                new StringRedisSerializer())
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
