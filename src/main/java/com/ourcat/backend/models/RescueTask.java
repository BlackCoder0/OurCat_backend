package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rescue_tasks", indexes = {
        @Index(columnList = "rescue_activity_id"),
        @Index(columnList = "assignee_user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescueTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rescue_activity_id", nullable = false)
    private Long rescueActivityId;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "assigner_user_id")
    private Long assignerUserId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "assigned";

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    @Column(name = "completion_images", length = 2000)
    private String completionImages;

    @PrePersist
    public void prePersist() {
        if (assignedAt == null) assignedAt = Instant.now();
    }
}
