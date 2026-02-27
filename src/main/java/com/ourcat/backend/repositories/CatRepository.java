package com.ourcat.backend.repositories;

import com.ourcat.backend.models.Cat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatRepository extends JpaRepository<Cat, Long> {
}
