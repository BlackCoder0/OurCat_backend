package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rescue_task_logs", indexes = {
        @Index(columnList = "rescue_task_id"),
        @Index(columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescueTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rescue_task_id", nullable = false)
    private Long rescueTaskId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "log_type", nullable = false, length = 32)
    @Builder.Default
    private String logType = "progress";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 2000)
    private String images;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
