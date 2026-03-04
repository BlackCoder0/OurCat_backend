package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cat_reports", indexes = {
    @Index(columnList = "lat, lng"),
    @Index(columnList = "cat_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "report_time")
    private Instant reportTime;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(length = 500)
    private String description;

    @Column(name = "cat_id")
    private Long catId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "match_confidence")
    private Float matchConfidence;

    @Column(name = "confirmed")
    @Builder.Default
    private Boolean confirmed = false;

    @Column(name = "ai_suggested_cat_id")
    private Long aiSuggestedCatId;
    
    @Column(length = 100)
    private String color;
    
    @Column(length = 200)
    private String feature;
    
    @Column(length = 200)
    private String personality;

    @PrePersist
    public void prePersist() {
        if (reportTime == null) reportTime = Instant.now();
    }
}
