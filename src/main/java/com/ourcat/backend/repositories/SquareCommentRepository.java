package com.ourcat.backend.repositories;

import com.ourcat.backend.models.SquareComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SquareCommentRepository extends JpaRepository<SquareComment, Long> {

    List<SquareComment> findBySquarePostIdOrderByCreatedAtAsc(Long squarePostId, Pageable pageable);

    void deleteBySquarePostId(Long squarePostId);
}
