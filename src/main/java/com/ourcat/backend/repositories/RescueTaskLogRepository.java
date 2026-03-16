package com.ourcat.backend.repositories;

import com.ourcat.backend.models.RescueTaskLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RescueTaskLogRepository extends JpaRepository<RescueTaskLog, Long> {

    List<RescueTaskLog> findByRescueTaskIdOrderByCreatedAtAsc(Long rescueTaskId);
}
