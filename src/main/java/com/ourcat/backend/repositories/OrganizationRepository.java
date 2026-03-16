package com.ourcat.backend.repositories;

import com.ourcat.backend.models.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
