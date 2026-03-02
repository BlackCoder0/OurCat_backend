package com.ourcat.backend.services;

import com.ourcat.backend.models.SquareComment;
import com.ourcat.backend.models.SquarePost;
import com.ourcat.backend.repositories.SquareCommentRepository;
import com.ourcat.backend.repositories.SquarePostRepository;
import com.ourcat.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SquareService {

    private final SquarePostRepository squarePostRepository;
    private final SquareCommentRepository squareCommentRepository;
    private final UserRepository userRepository;

    public Page<SquarePost> listPosts(int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size);
        if ("hot".equalsIgnoreCase(sort)) {
            return squarePostRepository.findAllByOrderByLikesDescCreatedAtDesc(pageable);
        }
        return squarePostRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Optional<SquarePost> getPost(Long id) {
        return squarePostRepository.findById(id);
    }

    @Transactional
    public SquarePost createPost(Long userId, String text, List<String> images, String location, String type) {
        String imagesJson = images == null || images.isEmpty() ? null
                : images.stream().collect(Collectors.joining("\",\"", "[\"", "\"]"));
        SquarePost post = SquarePost.builder()
                .text(text)
                .images(imagesJson)
                .location(location)
                .type(type != null ? type : "inquiry")
                .status("open")
                .likes(0) // Explicitly set default value to avoid null
                .userId(userId)
                .build();
        return squarePostRepository.save(post);
    }

    @Transactional
    public boolean markSolved(Long postId, Long userId, int userRole) {
        Optional<SquarePost> opt = squarePostRepository.findById(postId);
        if (opt.isEmpty())
            return false;
        SquarePost post = opt.get();
        // Only author can mark solved
        if (!post.getUserId().equals(userId))
            return false;
        post.setStatus("resolved");
        squarePostRepository.save(post);
        return true;
    }

    @Transactional
    public boolean deletePost(Long postId, Long userId, int userRole) {
        Optional<SquarePost> opt = squarePostRepository.findById(postId);
        if (opt.isEmpty())
            return false;
        SquarePost post = opt.get();

        // Check author role
        Optional<com.ourcat.backend.models.User> authorOpt = userRepository.findById(post.getUserId());
        int authorRole = authorOpt.map(com.ourcat.backend.models.User::getRole).orElse(1);

        // Role 3 (Admin) can delete all
        if (userRole >= 3) {
            squarePostRepository.delete(post);
            return true;
        }

        // Role 2 (Volunteer) can delete posts by Role 1 and Role 2
        if (userRole == 2) {
            if (authorRole <= 2) {
                squarePostRepository.delete(post);
                return true;
            }
            return false;
        }

        // Role 1 (User) can only delete their own posts
        if (post.getUserId().equals(userId)) {
            squarePostRepository.delete(post);
            return true;
        }

        return false;
    }

    public List<SquareComment> getComments(Long postId, int page, int size) {
        return squareCommentRepository.findBySquarePostIdOrderByCreatedAtAsc(postId, PageRequest.of(page, size));
    }

    @Transactional
    public SquareComment addComment(Long postId, Long userId, String content) {
        SquareComment c = new SquareComment();
        c.setSquarePostId(postId);
        c.setUserId(userId);
        c.setContent(content);
        return squareCommentRepository.save(c);
    }

    public Optional<com.ourcat.backend.models.User> getAuthor(Long userId) {
        return userRepository.findById(userId);
    }
}
