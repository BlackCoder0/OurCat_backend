package com.ourcat.backend.repositories;

import com.ourcat.backend.models.RescueContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RescueContactRepository extends JpaRepository<RescueContact, Long> {

    List<RescueContact> findAllByOrderBySortOrderAsc();
}
