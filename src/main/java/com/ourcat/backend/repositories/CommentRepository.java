package com.ourcat.backend.repositories;

import com.ourcat.backend.models.Comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);

    void deleteByPostId(Long postId);

    Page<Comment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.content LIKE %:q% ORDER BY c.createdAt DESC")
    Page<Comment> searchByKeyword(@Param("q") String q, Pageable pageable);
}
