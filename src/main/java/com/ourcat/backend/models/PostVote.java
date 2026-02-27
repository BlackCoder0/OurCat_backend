package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "post_votes", indexes = {
    @Index(columnList = "post_id")
})
@IdClass(PostVoteId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostVote {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "is_like", nullable = false)
    private Boolean isLike; // true = like, false = dislike
}
