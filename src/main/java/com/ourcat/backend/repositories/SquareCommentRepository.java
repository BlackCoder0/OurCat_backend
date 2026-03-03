package com.ourcat.backend.repositories;

import com.ourcat.backend.models.SquareComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SquareCommentRepository extends JpaRepository<SquareComment, Long> {

    List<SquareComment> findBySquarePostIdOrderByCreatedAtAsc(Long squarePostId, Pageable pageable);

    void deleteBySquarePostId(Long squarePostId);

    @Query("select distinct c.userId from SquareComment c where c.squarePostId = :postId")
    List<Long> findDistinctUserIdsBySquarePostId(@Param("postId") Long postId);
}
