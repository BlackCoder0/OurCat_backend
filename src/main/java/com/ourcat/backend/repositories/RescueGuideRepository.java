package com.ourcat.backend.repositories;

import com.ourcat.backend.models.RescueGuide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RescueGuideRepository extends JpaRepository<RescueGuide, Long> {

    List<RescueGuide> findAllByOrderBySortOrderAsc();
}
