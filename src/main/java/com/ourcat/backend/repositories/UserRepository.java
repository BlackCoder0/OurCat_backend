package com.ourcat.backend.repositories;

import com.ourcat.backend.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.username LIKE %:q% OR u.nickname LIKE %:q% ORDER BY u.createdAt DESC")
    Page<User> searchByKeyword(@Param("q") String q, Pageable pageable);
}
