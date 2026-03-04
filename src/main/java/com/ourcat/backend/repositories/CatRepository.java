package com.ourcat.backend.repositories;

import com.ourcat.backend.models.Cat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CatRepository extends JpaRepository<Cat, Long> {

    @Query("SELECT c FROM Cat c WHERE c.aiEmbedding IS NOT NULL")
    List<Cat> findAllWithEmbedding();

    @Query("SELECT c FROM Cat c WHERE c.name LIKE %?1% OR c.color LIKE %?1% OR c.feature LIKE %?1%")
    Page<Cat> searchByKeyword(String keyword, Pageable pageable);

    Page<Cat> findByStatus(String status, Pageable pageable);

    List<Cat> findByColorContaining(String color);
}
