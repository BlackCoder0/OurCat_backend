package com.ourcat.backend.repositories;

import com.ourcat.backend.models.PostVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PostVoteRepository extends JpaRepository<PostVote, com.ourcat.backend.models.PostVoteId> {

    Optional<PostVote> findByUserIdAndPostId(Long userId, Long postId);

    void deleteByPostId(Long postId);

    @Query("SELECT COUNT(v) FROM PostVote v WHERE v.postId = :postId AND v.isLike = true")
    long countLikesByPostId(Long postId);

    @Query("SELECT COUNT(v) FROM PostVote v WHERE v.postId = :postId AND v.isLike = false")
    long countDislikesByPostId(Long postId);
}
