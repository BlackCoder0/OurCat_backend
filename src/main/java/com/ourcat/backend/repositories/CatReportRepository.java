package com.ourcat.backend.repositories;

import com.ourcat.backend.models.CatReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatReportRepository extends JpaRepository<CatReport, Long> {

    List<CatReport> findAllByOrderByReportTimeDesc();

    List<CatReport> findByUserIdOrderByReportTimeDesc(Long userId);

    List<CatReport> findByCatIdOrderByReportTimeDesc(Long catId);

    Optional<CatReport> findFirstByCatIdOrderByReportTimeDesc(Long catId);

    long countByUserId(Long userId);

    long countByCatId(Long catId);
}
