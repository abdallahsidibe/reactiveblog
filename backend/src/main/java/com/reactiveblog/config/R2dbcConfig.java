package com.reactiveblog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/*
 * Configuration R2DBC.
 *
 * Spring Boot auto-configure la ConnectionFactory à partir de application.yml :
 *   spring.r2dbc.url / username / password / pool.*
 *
 * On n'a donc pas besoin de déclarer manuellement le ConnectionFactory Bean.
 * Cette classe ajoute uniquement :
 *
 *   - @EnableR2dbcRepositories : active le scan des ReactiveCrudRepository
 *     dans le package "repository". Spring génère les implémentations
 *     au démarrage (comme JPA, mais en version réactive).
 *
 *   - @EnableR2dbcAuditing : activera @CreatedDate / @LastModifiedDate
 *     sur l'entité Article (rempli automatiquement par Spring Data).
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.reactiveblog.repository")
@EnableR2dbcAuditing
public class R2dbcConfig {
}
