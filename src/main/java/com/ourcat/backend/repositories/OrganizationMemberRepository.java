package com.ourcat.backend.repositories;

import com.ourcat.backend.models.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    List<OrganizationMember> findByOrganizationIdOrderByJoinedAtAsc(Long organizationId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

    void deleteByOrganizationIdAndUserId(Long organizationId, Long userId);
}
