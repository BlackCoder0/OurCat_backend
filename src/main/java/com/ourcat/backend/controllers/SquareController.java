package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.SquareComment;
import com.ourcat.backend.models.SquarePost;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.RescueActivityRepository;
import com.ourcat.backend.services.RescueService;
import com.ourcat.backend.services.SquareService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/square")
@RequiredArgsConstructor
public class SquareController {

    private final SquareService squareService;
    private final CatRepository catRepository;
    private final RescueActivityRepository rescueActivityRepository;
    private final RescueService rescueService;

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        Page<SquarePost> pageResult = squareService.listPosts(page, size, sort);
        List<Map<String, Object>> items = pageResult.getContent().stream().map(post -> {
            Map<String, Object> item = new HashMap<>(postToMap(post));
            if (post.getReferencedCatId() != null) {
                catRepository.findById(post.getReferencedCatId()).ifPresent(cat -> item.put("referencedCat", catToRefMap(cat)));
            }
            return item;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "content", items,
                "totalPages", pageResult.getTotalPages(),
                "totalElements", pageResult.getTotalElements()));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id) {
        Optional<SquarePost> opt = squareService.getPost(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SquarePost post = opt.get();
        Map<String, Object> result = new HashMap<>(postToMap(post));
        squareService.getAuthor(post.getUserId()).ifPresent(user -> {
            result.put("authorName", user.getNickname() != null ? user.getNickname() : user.getUsername());
            result.put("authorAvatar", user.getAvatarUrl());
        });
        if (post.getReferencedCatId() != null) {
            catRepository.findById(post.getReferencedCatId()).ifPresent(cat -> result.put("referencedCat", catToRefMap(cat)));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody SquarePostRequest req) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Long refCatId = req.getReferencedCatId() != null && req.getReferencedCatId() > 0 ? req.getReferencedCatId() : null;
            SquarePost post = squareService.createPost(
                    principal.getUser().getId(),
                    req.getText(),
                    req.getImages(),
                    req.getLocation(),
                    req.getType() != null ? req.getType() : "inquiry",
                    refCatId,
                    req.getProblemType());
            return ResponseEntity.ok(postToMap(post));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/posts/{id}/markSolved")
    public ResponseEntity<?> markSolved(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        boolean ok = squareService.markSolved(id, principal.getUser().getId(), principal.getUser().getRole());
        if (!ok) {
            return ResponseEntity.status(403).body(Map.of("message", "无权限"));
        }
        return squareService.getPost(id)
                .map(post -> ResponseEntity.ok(postToMap(post)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "50") int size) {
        List<SquareComment> comments = squareService.getComments(id, page, size);
        List<Map<String, Object>> result = comments.stream().map(comment -> {
            Map<String, Object> item = new HashMap<>(Map.of(
                    "id", comment.getId(),
                    "content", comment.getContent(),
                    "userId", comment.getUserId(),
                    "createdAt", comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : ""));
            squareService.getAuthor(comment.getUserId()).ifPresent(user -> {
                item.put("authorName", user.getNickname() != null ? user.getNickname() : user.getUsername());
                item.put("authorAvatar", user.getAvatarUrl());
            });
            return item;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id,
                                        @Valid @RequestBody CommentRequest req) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        SquareComment comment = squareService.addComment(id, principal.getUser().getId(), req.getContent());
        Map<String, Object> result = new HashMap<>(Map.of(
                "id", comment.getId(),
                "content", comment.getContent(),
                "userId", comment.getUserId(),
                "createdAt", comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : ""));
        result.put("authorName", principal.getUser().getNickname() != null
                ? principal.getUser().getNickname()
                : principal.getUser().getUsername());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long id) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        boolean ok = squareService.deletePost(id, principal.getUser().getId(), principal.getUser().getRole());
        if (!ok) {
            return ResponseEntity.status(403).body(Map.of("message", "无权限或帖子不存在"));
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my/comments")
    public ResponseEntity<?> myComments(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        Page<SquareComment> p = squareService.getCommentsByUserId(principal.getUser().getId(), page, size);
        List<Map<String, Object>> items = p.getContent().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("squarePostId", c.getSquarePostId());
            m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            m.put("source", "square");
            squareService.getPost(c.getSquarePostId()).ifPresent(post -> m.put("postTitle", snippet(post.getText())));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "content", items,
                "totalPages", p.getTotalPages(),
                "totalElements", p.getTotalElements()));
    }

    private Map<String, Object> postToMap(SquarePost post) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", post.getId());
        result.put("text", post.getText());
        result.put("images", post.getImages() != null ? post.getImages() : "[]");
        result.put("location", post.getLocation() != null ? post.getLocation() : "");
        result.put("type", post.getType());
        result.put("status", post.getStatus());
        result.put("likes", post.getLikes());
        result.put("userId", post.getUserId());
        result.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");
        result.put("referencedCatId", post.getReferencedCatId());
        result.put("rescueActivityId", post.getRescueActivityId() != null ? post.getRescueActivityId() : 0);
        if (post.getRescueActivityId() != null) {
            rescueActivityRepository.findById(post.getRescueActivityId()).ifPresent(activity -> {
                result.put("rescueActivityTitle", activity.getTitle());
                result.put("rescueActivityStatus", activity.getStatus());
                result.put("rescueActivityCreatedBy", activity.getCreatedBy());
                result.put("rescueTasks", rescueService.getTasksByActivityId(activity.getId()).stream()
                        .map(rescueService::taskToMap)
                        .collect(Collectors.toList()));
            });
        }
        return result;
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (t.length() <= 20) {
            return t;
        }
        return t.substring(0, 20) + "...";
    }

    private Map<String, Object> catToRefMap(Cat cat) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", cat.getId());
        result.put("name", cat.getName() != null ? cat.getName() : "");
        result.put("primaryImageUrl", cat.getPrimaryImageUrl() != null ? cat.getPrimaryImageUrl() : "");
        result.put("color", cat.getColor() != null ? cat.getColor() : "");
        result.put("status", cat.getStatus() != null ? cat.getStatus() : "active");
        return result;
    }

    @Data
    public static class SquarePostRequest {
        @NotBlank
        private String text;
        private List<String> images;
        private String location;
        private String type;
        private Long referencedCatId;
        private String problemType;
    }

    @Data
    public static class CommentRequest {
        @NotBlank
        private String content;
    }
}
