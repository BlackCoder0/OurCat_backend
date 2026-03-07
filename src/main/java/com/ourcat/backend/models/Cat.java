package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String name;

    @Column(length = 64)
    private String color;

    @Column(columnDefinition = "TEXT")
    private String feature;

    @Column(length = 64)
    private String personality;

    @Column(length = 20)
    @Builder.Default
    private String status = "active";

    @Column(name = "primary_image_url", length = 500)
    private String primaryImageUrl;

    @Column(name = "report_count")
    @Builder.Default
    private Integer reportCount = 0;

    @Lob
    @Column(name = "ai_embedding")
    private byte[] aiEmbedding;

    @Column(name = "ai_embedding_model", length = 100)
    private String aiEmbeddingModel;

    @Column(name = "ai_embedding_dim")
    private Integer aiEmbeddingDim;

    @Column(name = "ai_embedding_updated_at")
    private Instant aiEmbeddingUpdatedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
