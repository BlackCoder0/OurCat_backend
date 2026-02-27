package com.ourcat.backend.repositories;

import com.ourcat.backend.models.CatReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatReportRepository extends JpaRepository<CatReport, Long> {

    List<CatReport> findAllByOrderByReportTimeDesc();
}
