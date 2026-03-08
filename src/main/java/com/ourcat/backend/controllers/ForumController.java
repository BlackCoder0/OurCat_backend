package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.Comment;
import com.ourcat.backend.models.Post;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.UserRepository;
import com.ourcat.backend.services.ForumService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;
    private final UserRepository userRepository;
    private final CatRepository catRepository;

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        Page<Post> p = forumService.listPosts(page, size, search);
        List<Map<String, Object>> items = p.getContent().stream().map(post -> {
            Map<String, Object> m = new HashMap<>(postToMap(post));
            forumService.getPostAuthor(post.getUserId())
                    .ifPresent(u -> m.put("authorName", u.getNickname() != null ? u.getNickname() : u.getUsername()));
            if (post.getReferencedCatId() != null) {
                catRepository.findById(post.getReferencedCatId()).ifPresent(cat -> m.put("referencedCat", catToRefMap(cat)));
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "content", items,
                "totalPages", p.getTotalPages(),
                "totalElements", p.getTotalElements()));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        Optional<Post> opt = forumService.getPost(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();
        Post post = opt.get();
        Map<String, Object> map = new HashMap<>(postToMap(post));
        forumService.getPostAuthor(post.getUserId()).ifPresent(u -> {
            map.put("authorName", u.getNickname() != null ? u.getNickname() : u.getUsername());
            map.put("authorAvatar", u.getAvatarUrl());
        });
        if (post.getReferencedCatId() != null) {
            catRepository.findById(post.getReferencedCatId()).ifPresent(cat -> map.put("referencedCat", catToRefMap(cat)));
        }
        if (principal != null && principal.getUser() != null) {
            map.put("userVote", forumService.getCurrentUserVote(principal.getUser().getId(), id));
        }
        return ResponseEntity.ok(map);
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PostRequest req) {
        if (principal == null || principal.getUser() == null)
            return ResponseEntity.status(401).build();
        List<String> images = req.getImages() != null ? req.getImages() : new ArrayList<>();
        Long refCatId = req.getReferencedCatId() != null && req.getReferencedCatId() > 0 ? req.getReferencedCatId() : null;
        Post post = forumService.createPost(principal.getUser().getId(), req.getTitle(), req.getContent(), images, refCatId);
        return ResponseEntity.ok(postToMap(post));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<?> updatePost(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PostRequest req) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Long userId = principal.getUser().getId();
        Long refCatId = req.getReferencedCatId() != null && req.getReferencedCatId() > 0 ? req.getReferencedCatId() : null;
        Optional<Post> updated = forumService.updatePost(id, userId, req.getTitle(), req.getContent(), req.getImages(), refCatId);
        if (updated.isEmpty())
            return ResponseEntity.status(403).body(Map.of("message", "无权限或帖子不存在"));
        return ResponseEntity.ok(postToMap(updated.get()));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        boolean ok = forumService.deletePost(id, principal.getUser().getId(), principal.getUser().getRole());
        if (!ok)
            return ResponseEntity.status(403).body(Map.of("message", "无权限或帖子不存在"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/pin")
    public ResponseEntity<?> setPinned(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean pinned) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        if (principal.getUser().getRole() < 2)
            return ResponseEntity.status(403).build();
        boolean ok = forumService.setPinned(id, principal.getUser().getId(), principal.getUser().getRole(), pinned);
        if (!ok)
            return ResponseEntity.notFound().build();
        return forumService.getPost(id).map(p -> ResponseEntity.ok(postToMap(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<?> like(@PathVariable Long id, @RequestParam boolean like,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUser() == null)
            return ResponseEntity.status(401).build();
        Optional<Post> opt = forumService.like(principal.getUser().getId(), id, like);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();
        Post p = opt.get();
        int userVote = forumService.getCurrentUserVote(principal.getUser().getId(), id);
        return ResponseEntity.ok(Map.of("likes", p.getLikes(), "dislikes", p.getDislikes(), "userVote", userVote));
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Comment> comments = forumService.getComments(id, page, size);
        List<Map<String, Object>> list = comments.stream().map(c -> {
            Map<String, Object> m = new HashMap<>(Map.of(
                    "id", c.getId(),
                    "content", c.getContent(),
                    "userId", c.getUserId(),
                    "createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""));
            forumService.getPostAuthor(c.getUserId()).ifPresent(u -> {
                m.put("authorName", u.getNickname() != null ? u.getNickname() : u.getUsername());
                m.put("authorAvatar", u.getAvatarUrl());
            });
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CommentRequest req) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Comment c = forumService.addComment(id, principal.getUser().getId(), req.getContent());
        Map<String, Object> m = new HashMap<>(Map.of(
                "id", c.getId(),
                "content", c.getContent(),
                "userId", c.getUserId(),
                "createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""));
        m.put("authorName", principal.getUser().getNickname() != null ? principal.getUser().getNickname()
                : principal.getUser().getUsername());
        return ResponseEntity.ok(m);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "post") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "搜索关键词不能为空"));
        }
        String keyword = q.trim();
        if ("comment".equals(type)) {
            Page<Comment> p = forumService.searchComments(keyword, page, size);
            List<Map<String, Object>> items = p.getContent().stream().map(c -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", c.getId());
                m.put("content", c.getContent());
                m.put("postId", c.getPostId());
                m.put("userId", c.getUserId());
                m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
                forumService.getPostAuthor(c.getUserId()).ifPresent(
                        u -> m.put("authorName", u.getNickname() != null ? u.getNickname() : u.getUsername()));
                forumService.getPost(c.getPostId()).ifPresent(post -> m.put("postTitle", post.getTitle()));
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(
                    Map.of("content", items, "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements()));
        } else if ("user".equals(type)) {
            Page<User> p = userRepository.searchByKeyword(keyword, PageRequest.of(page, size));
            List<Map<String, Object>> items = p.getContent().stream().map(u -> Map.<String, Object>of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "nickname", u.getNickname() != null ? u.getNickname() : u.getUsername(),
                    "avatarUrl", u.getAvatarUrl() != null ? u.getAvatarUrl() : "",
                    "role", u.getRole())).collect(Collectors.toList());
            return ResponseEntity.ok(
                    Map.of("content", items, "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements()));
        } else {
            Page<Post> p = forumService.listPosts(page, size, keyword);
            List<Map<String, Object>> items = p.getContent().stream().map(post -> {
                Map<String, Object> m = new HashMap<>(postToMap(post));
                forumService.getPostAuthor(post.getUserId()).ifPresent(u -> {
                    m.put("authorName", u.getNickname() != null ? u.getNickname() : u.getUsername());
                    m.put("authorAvatar", u.getAvatarUrl());
                });
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(
                    Map.of("content", items, "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements()));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long commentId) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        boolean ok = forumService.deleteComment(commentId, principal.getUser().getId(), principal.getUser().getRole());
        if (!ok)
            return ResponseEntity.status(403).body(Map.of("message", "无权限或评论不存在"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my/posts")
    public ResponseEntity<?> myPosts(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Page<Post> p = forumService.getPostsByUserId(principal.getUser().getId(), page, size);
        List<Map<String, Object>> items = p.getContent().stream().map(post -> {
            Map<String, Object> m = new HashMap<>(postToMap(post));
            m.put("authorName", principal.getUser().getNickname() != null ? principal.getUser().getNickname()
                    : principal.getUser().getUsername());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity
                .ok(Map.of("content", items, "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements()));
    }

    @GetMapping("/my/comments")
    public ResponseEntity<?> myComments(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        Page<Comment> p = forumService.getCommentsByUserId(principal.getUser().getId(), page, size);
        List<Map<String, Object>> items = p.getContent().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("postId", c.getPostId());
            m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            forumService.getPost(c.getPostId()).ifPresent(post -> m.put("postTitle", post.getTitle()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity
                .ok(Map.of("content", items, "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements()));
    }

    private Map<String, Object> postToMap(Post post) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", post.getId());
        m.put("title", post.getTitle());
        m.put("content", post.getContent() != null ? post.getContent() : "");
        m.put("images", post.getImages() != null ? post.getImages() : "[]");
        m.put("likes", post.getLikes());
        m.put("dislikes", post.getDislikes());
        m.put("userId", post.getUserId());
        m.put("pinned", post.getPinned());
        m.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");
        m.put("referencedCatId", post.getReferencedCatId());
        return m;
    }

    private Map<String, Object> catToRefMap(Cat cat) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", cat.getId());
        m.put("name", cat.getName() != null ? cat.getName() : "");
        m.put("primaryImageUrl", cat.getPrimaryImageUrl() != null ? cat.getPrimaryImageUrl() : "");
        m.put("color", cat.getColor() != null ? cat.getColor() : "");
        m.put("status", cat.getStatus() != null ? cat.getStatus() : "active");
        return m;
    }

    @Data
    public static class PostRequest {
        @NotBlank
        @Size(max = 200)
        private String title;
        private String content;
        private List<String> images;
        private Long referencedCatId;
    }

    @Data
    public static class CommentRequest {
        @NotBlank
        private String content;
    }
}
