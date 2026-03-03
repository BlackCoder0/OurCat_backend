package com.ourcat.backend.services;

import com.ourcat.backend.models.Comment;
import com.ourcat.backend.models.Post;
import com.ourcat.backend.models.PostVote;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.CommentRepository;
import com.ourcat.backend.repositories.PostRepository;
import com.ourcat.backend.repositories.PostVoteRepository;
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
    private final PostVoteRepository postVoteRepository;
    private final MessageService messageService;

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
                .likes(0)
                .dislikes(0)
                .pinned(false)
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
        
        // 查询帖子作者的角色
        Optional<User> authorOpt = userRepository.findById(post.getUserId());
        int authorRole = authorOpt.map(User::getRole).orElse(1);
        
        // 如果帖子作者是管理员(role=3)，只有管理员可以删除
        if (authorRole >= 3 && userRole < 3) {
            return false;
        }
        
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

    /**
     * Set or toggle the current user's vote: one vote per user (like or dislike, or none).
     * If user clicks same again, vote is removed; if opposite, vote is switched.
     */
    @Transactional
    public Optional<Post> like(Long userId, Long postId, boolean like) {
        Optional<Post> opt = postRepository.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Optional<PostVote> existing = postVoteRepository.findByUserIdAndPostId(userId, postId);
        if (existing.isPresent()) {
            PostVote v = existing.get();
            if (v.getIsLike() == like) {
                postVoteRepository.delete(v);
            } else {
                v.setIsLike(like);
                postVoteRepository.save(v);
            }
        } else {
            postVoteRepository.save(new PostVote(userId, postId, like));
        }
        int likesCount = (int) postVoteRepository.countLikesByPostId(postId);
        int dislikesCount = (int) postVoteRepository.countDislikesByPostId(postId);
        Post post = opt.get();
        post.setLikes(likesCount);
        post.setDislikes(dislikesCount);
        return Optional.of(postRepository.save(post));
    }

    /** Returns current user's vote: 1 = liked, -1 = disliked, 0 = none. */
    public int getCurrentUserVote(Long userId, Long postId) {
        return postVoteRepository.findByUserIdAndPostId(userId, postId)
                .map(v -> v.getIsLike() ? 1 : -1)
                .orElse(0);
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
        Comment saved = commentRepository.save(c);
        postRepository.findById(postId).ifPresent(post -> {
            if (!post.getUserId().equals(userId)) {
                String title = post.getTitle() != null ? post.getTitle() : "";
                String text = title.isEmpty() ? "有人回复了你的帖子" : "有人回复了你的帖子: " + title;
                messageService.create(post.getUserId(), "forum_reply", text, "forum_post", postId);
            }
        });
        return saved;
    }

    public Page<Post> searchByComment(String q, int page, int size) {
        return postRepository.searchByComment(q.trim(), PageRequest.of(page, size));
    }

    public Page<Comment> searchComments(String q, int page, int size) {
        return commentRepository.searchByKeyword(q.trim(), PageRequest.of(page, size));
    }

    public Page<Post> getPostsByUserId(Long userId, int page, int size) {
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public Page<Comment> getCommentsByUserId(Long userId, int page, int size) {
        return commentRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional
    public boolean deleteComment(Long commentId, Long userId, int userRole) {
        Optional<Comment> opt = commentRepository.findById(commentId);
        if (opt.isEmpty()) return false;
        Comment c = opt.get();
        
        // 查询评论作者的角色
        Optional<User> authorOpt = userRepository.findById(c.getUserId());
        int authorRole = authorOpt.map(User::getRole).orElse(1);
        
        // 如果评论作者是管理员(role=3)，只有管理员可以删除
        if (authorRole >= 3 && userRole < 3) {
            return false;
        }
        
        if (c.getUserId().equals(userId) || userRole >= 2) {
            commentRepository.delete(c);
            return true;
        }
        return false;
    }

    public Optional<User> getPostAuthor(Long userId) {
        return userRepository.findById(userId);
    }
}
