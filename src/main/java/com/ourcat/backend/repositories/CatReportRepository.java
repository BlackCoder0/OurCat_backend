package com.ourcat.backend.repositories;

import com.ourcat.backend.models.CatReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatReportRepository extends JpaRepository<CatReport, Long> {

    List<CatReport> findAllByOrderByReportTimeDesc();

    List<CatReport> findByUserIdOrderByReportTimeDesc(Long userId);

    List<CatReport> findByCatIdOrderByReportTimeDesc(Long catId);

    long countByUserId(Long userId);

    long countByCatId(Long catId);
}
