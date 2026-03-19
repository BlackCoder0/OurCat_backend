package com.ourcat.backend.repositories;

import com.ourcat.backend.models.RescueActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RescueActivityRepository extends JpaRepository<RescueActivity, Long> {

    Page<RescueActivity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<RescueActivity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<RescueActivity> findByCatIdNotNullAndStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<RescueActivity> findByCatIdAndStatusInOrderByCreatedAtDesc(Long catId, List<String> statuses);

    List<RescueActivity> findByCatIdInAndStatusInOrderByCreatedAtDesc(List<Long> catIds, List<String> statuses);

    List<RescueActivity> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<RescueActivity> findBySquarePostIdNotNullAndStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<RescueActivity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<RescueActivity> findByOrganizationIdAndStatusInOrderByCreatedAtDesc(Long organizationId, List<String> statuses);

    List<RescueActivity> findByCatIdOrderByCreatedAtDesc(Long catId);

    long countByStatus(String status);

    long countByStatusIn(List<String> statuses);
}
