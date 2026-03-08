package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts", indexes = {
    @Index(columnList = "user_id"),
    @Index(columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String images; // JSON array of URLs

    @Column(nullable = false)
    @Builder.Default
    private Integer likes = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer dislikes = 0;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean pinned = false;

    @Column(name = "created_at")
    private Instant createdAt;

    /** 引用的猫咪档案 ID（可选，用于论坛帖关联猫咪） */
    @Column(name = "referenced_cat_id")
    private Long referencedCatId;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
