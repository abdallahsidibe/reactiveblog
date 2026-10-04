package com.reactiveblog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/*
 * Configuration MongoDB.
 *
 * Spring Boot auto-configure le MongoClient à partir de application.yml :
 *   spring.data.mongodb.uri
 *
 * Cette classe ajoute :
 *
 *   - @EnableReactiveMongoRepositories : active le scan des ReactiveMongoRepository
 *     dans le package "repository". Spring génère les implémentations au démarrage.
 *
 *   - @EnableMongoAuditing : active @CreatedDate / @LastModifiedDate
 *     sur l'entité Article (rempli automatiquement par Spring Data).
 */
@Configuration
@EnableReactiveMongoRepositories(basePackages = "com.reactiveblog.repository")
@EnableMongoAuditing
public class MongoConfig {
}
