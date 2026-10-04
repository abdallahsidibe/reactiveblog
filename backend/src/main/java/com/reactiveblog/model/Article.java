package com.reactiveblog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/*
 * Entité MongoDB.
 *
 * Différences clés avec une entité R2DBC :
 *
 *  R2DBC (@Table)                  │  MongoDB (@Document)
 * ─────────────────────────────────┼──────────────────────────────────────
 *  @Table("articles")              │  @Document(collection = "articles")
 *  id: Long (BIGSERIAL PostgreSQL) │  id: String (ObjectId MongoDB, 24 hex)
 *  @Column("created_at")           │  Pas nécessaire (champs = noms Java)
 *  Flyway pour le schéma           │  Pas de schéma (schemaless)
 *  Connexions JDBC + R2DBC         │  Driver Reactive MongoDB uniquement
 *
 * L'id est null à la création : MongoDB génère automatiquement un ObjectId.
 * Spring Data le mappe sur un String via le codec BSON intégré.
 */
@Document(collection = "articles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    /*
     * @Id : Spring Data MongoDB identifie le champ _id du document.
     * Null à la création → MongoDB génère un ObjectId (24 chars hex).
     */
    @Id
    private String id;

    private String title;

    private String content;

    private String author;

    /*
     * @CreatedDate : rempli automatiquement par @EnableMongoAuditing
     * lors du premier save(). On ne le définit jamais manuellement.
     */
    @CreatedDate
    private Instant createdAt;

    /*
     * @LastModifiedDate : mis à jour automatiquement par Spring Data
     * à chaque save().
     */
    @LastModifiedDate
    private Instant updatedAt;
}
