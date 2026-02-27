package com.ourcat.backend.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostVoteId implements Serializable {
    private Long userId;
    private Long postId;
}
