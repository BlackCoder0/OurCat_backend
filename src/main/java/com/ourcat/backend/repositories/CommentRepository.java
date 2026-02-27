package com.ourcat.backend.repositories;

import com.ourcat.backend.models.Comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);

    void deleteByPostId(Long postId);
}
