package com.ourcat.backend.repositories;

import com.ourcat.backend.models.SquarePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquarePostRepository extends JpaRepository<SquarePost, Long> {

    Page<SquarePost> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SquarePost> findAllByOrderByLikesDescCreatedAtDesc(Pageable pageable);

    long countByUserId(Long userId);
}
