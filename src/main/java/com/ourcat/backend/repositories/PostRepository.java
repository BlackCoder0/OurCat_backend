package com.ourcat.backend.repositories;

import com.ourcat.backend.models.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.title LIKE %:q% OR p.content LIKE %:q% ORDER BY p.pinned DESC, p.createdAt DESC")
    Page<Post> search(@Param("q") String q, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Post p JOIN Comment c ON c.postId = p.id WHERE c.content LIKE %:q% ORDER BY p.pinned DESC, p.createdAt DESC")
    Page<Post> searchByComment(@Param("q") String q, Pageable pageable);

    Page<Post> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);
}
