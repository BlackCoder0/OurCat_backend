package com.ourcat.backend.repositories;

import com.ourcat.backend.models.RescueArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RescueArticleRepository extends JpaRepository<RescueArticle, Long> {

    List<RescueArticle> findAllByOrderBySortOrderAsc();

    List<RescueArticle> findByCategoryOrderBySortOrderAsc(String category);
}
