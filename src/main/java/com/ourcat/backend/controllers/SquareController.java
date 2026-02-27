package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.SquareComment;
import com.ourcat.backend.models.SquarePost;
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

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        Page<SquarePost> p = squareService.listPosts(page, size, sort);
        List<Map<String, Object>> items = p.getContent().stream().map(this::postToMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "content", items,
                "totalPages", p.getTotalPages(),
                "totalElements", p.getTotalElements()
        ));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id) {
        Optional<SquarePost> opt = squareService.getPost(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> map = new HashMap<>(postToMap(opt.get()));
        squareService.getAuthor(opt.get().getUserId()).ifPresent(u ->
                map.put("authorName", u.getNickname() != null ? u.getNickname() : u.getUsername()));
        return ResponseEntity.ok(map);
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody SquarePostRequest req) {
        if (principal == null) return ResponseEntity.status(401).build();
        SquarePost post = squareService.createPost(
                principal.getUser().getId(),
                req.getText(), req.getImages(), req.getLocation(),
                req.getType() != null ? req.getType() : "inquiry");
        return ResponseEntity.ok(postToMap(post));
    }

    @PostMapping("/posts/{id}/markSolved")
    public ResponseEntity<?> markSolved(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(401).build();
        boolean ok = squareService.markSolved(id, principal.getUser().getId(), principal.getUser().getRole());
        if (!ok) return ResponseEntity.status(403).body(Map.of("message", "无权限"));
        return squareService.getPost(id).map(p -> ResponseEntity.ok(postToMap(p))).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "50") int size) {
        List<SquareComment> comments = squareService.getComments(id, page, size);
        List<Map<String, Object>> list = comments.stream().map(c -> {
            Map<String, Object> m = new HashMap<>(Map.of(
                    "id", c.getId(),
                    "content", c.getContent(),
                    "userId", c.getUserId(),
                    "createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""
            ));
            squareService.getAuthor(c.getUserId()).ifPresent(u -> m.put("authorName", u.getNickname() != null ? u.getNickname() : u.getUsername()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id,
                                       @Valid @RequestBody CommentRequest req) {
        if (principal == null) return ResponseEntity.status(401).build();
        SquareComment c = squareService.addComment(id, principal.getUser().getId(), req.getContent());
        Map<String, Object> m = new HashMap<>(Map.of(
                "id", c.getId(),
                "content", c.getContent(),
                "userId", c.getUserId(),
                "createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""
        ));
        m.put("authorName", principal.getUser().getNickname() != null ? principal.getUser().getNickname() : principal.getUser().getUsername());
        return ResponseEntity.ok(m);
    }

    private Map<String, Object> postToMap(SquarePost post) {
        return Map.of(
                "id", post.getId(),
                "text", post.getText(),
                "images", post.getImages() != null ? post.getImages() : "[]",
                "location", post.getLocation() != null ? post.getLocation() : "",
                "type", post.getType(),
                "status", post.getStatus(),
                "likes", post.getLikes(),
                "userId", post.getUserId(),
                "createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : ""
        );
    }

    @Data
    public static class SquarePostRequest {
        @NotBlank private String text;
        private java.util.List<String> images;
        private String location;
        private String type; // inquiry, rescue
    }

    @Data
    public static class CommentRequest {
        @NotBlank private String content;
    }
}
