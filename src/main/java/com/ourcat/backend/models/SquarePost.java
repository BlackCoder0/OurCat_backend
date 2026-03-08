package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "square_posts", indexes = {
    @Index(columnList = "created_at"),
    @Index(columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SquarePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(length = 2000)
    private String images;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, length = 32)
    private String type; // inquiry, rescue

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "open"; // open, resolved

    @Column(nullable = false)
    @Builder.Default
    private Integer likes = 0;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at")
    private Instant createdAt;

    /** 引用的猫咪档案 ID（可选，用于广场广播关联猫咪） */
    @Column(name = "referenced_cat_id")
    private Long referencedCatId;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
