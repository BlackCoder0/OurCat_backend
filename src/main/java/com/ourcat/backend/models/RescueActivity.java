package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rescue_activities", indexes = {
        @Index(columnList = "cat_id"),
        @Index(columnList = "status"),
        @Index(columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescueActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cat_id")
    private Long catId;

    @Column(name = "square_post_id")
    private Long squarePostId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String urgency = "normal";

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "created";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
