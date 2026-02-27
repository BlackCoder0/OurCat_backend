package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

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

    @Column(length = 64)
    private String color;

    @Column(columnDefinition = "TEXT")
    private String feature;

    @Column(length = 64)
    private String personality;

    @Lob
    @Column(name = "ai_embedding")
    private byte[] aiEmbedding;
}
