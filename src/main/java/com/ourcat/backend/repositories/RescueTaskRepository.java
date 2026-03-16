package com.ourcat.backend.repositories;

import com.ourcat.backend.models.RescueTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RescueTaskRepository extends JpaRepository<RescueTask, Long> {

    List<RescueTask> findByRescueActivityIdOrderByAssignedAtAsc(Long rescueActivityId);

    List<RescueTask> findByAssigneeUserIdOrderByAssignedAtDesc(Long assigneeUserId);

    List<RescueTask> findByRescueActivityIdIn(List<Long> rescueActivityIds);

    boolean existsByRescueActivityIdAndAssigneeUserId(Long rescueActivityId, Long assigneeUserId);
}
