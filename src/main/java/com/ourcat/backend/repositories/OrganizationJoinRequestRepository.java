package com.ourcat.backend.repositories;

import com.ourcat.backend.models.OrganizationJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationJoinRequestRepository extends JpaRepository<OrganizationJoinRequest, Long> {

    List<OrganizationJoinRequest> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, String status);

    Optional<OrganizationJoinRequest> findByOrganizationIdAndUserIdAndStatus(Long organizationId, Long userId, String status);

    boolean existsByOrganizationIdAndUserIdAndStatus(Long organizationId, Long userId, String status);
}
