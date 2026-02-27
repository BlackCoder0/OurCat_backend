package com.ourcat.backend.services;

import com.ourcat.backend.models.Comment;
import com.ourcat.backend.models.Post;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.CommentRepository;
import com.ourcat.backend.repositories.PostRepository;
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
public class ForumService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public Page<Post> listPosts(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size);
        if (search != null && !search.trim().isEmpty()) {
            return postRepository.search(search.trim(), pageable);
        }
        return postRepository.findAllByOrderByPinnedDescCreatedAtDesc(pageable);
    }

    public Optional<Post> getPost(Long id) {
        return postRepository.findById(id);
    }

    @Transactional
    public Post createPost(Long userId, String title, String content, List<String> imageUrls) {
        String imagesJson = imageUrls == null || imageUrls.isEmpty() ? null :
                imageUrls.stream().collect(Collectors.joining("\",\"", "[\"", "\"]"));
        Post post = Post.builder()
                .title(title)
                .content(content)
                .images(imagesJson)
                .userId(userId)
                .build();
        return postRepository.save(post);
    }

    @Transactional
    public Optional<Post> updatePost(Long postId, Long userId, String title, String content, List<String> imageUrls) {
        Optional<Post> opt = postRepository.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Post post = opt.get();
        if (!post.getUserId().equals(userId)) return Optional.empty();
        post.setTitle(title);
        post.setContent(content);
        if (imageUrls != null) {
            post.setImages(imageUrls.isEmpty() ? null :
                    imageUrls.stream().collect(Collectors.joining("\",\"", "[\"", "\"]")));
        }
        return Optional.of(postRepository.save(post));
    }

    @Transactional
    public boolean deletePost(Long postId, Long userId, int userRole) {
        Optional<Post> opt = postRepository.findById(postId);
        if (opt.isEmpty()) return false;
        Post post = opt.get();
        if (post.getUserId().equals(userId) || userRole >= 2) {
            commentRepository.deleteByPostId(postId);
            postRepository.delete(post);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean setPinned(Long postId, Long userId, int userRole, boolean pinned) {
        if (userRole < 2) return false;
        Optional<Post> opt = postRepository.findById(postId);
        if (opt.isEmpty()) return false;
        Post post = opt.get();
        post.setPinned(pinned);
        postRepository.save(post);
        return true;
    }

    @Transactional
    public Optional<Post> like(Long postId, boolean like) {
        Optional<Post> opt = postRepository.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Post post = opt.get();
        if (like) post.setLikes(post.getLikes() + 1);
        else post.setDislikes(post.getDislikes() + 1);
        return Optional.of(postRepository.save(post));
    }

    public List<Comment> getComments(Long postId, int page, int size) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId, PageRequest.of(page, size));
    }

    @Transactional
    public Comment addComment(Long postId, Long userId, String content) {
        Comment c = new Comment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setContent(content);
        return commentRepository.save(c);
    }

    public Optional<User> getPostAuthor(Long userId) {
        return userRepository.findById(userId);
    }
}
