package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.ourcat.backend.repositories.PostRepository postRepository;
    private final com.ourcat.backend.repositories.CommentRepository commentRepository;
    private final com.ourcat.backend.repositories.CatReportRepository catReportRepository;
    private final com.ourcat.backend.repositories.SquarePostRepository squarePostRepository;
    private final com.ourcat.backend.repositories.SquareCommentRepository squareCommentRepository;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userRepository.findById(principal.getUser().getId()).orElse(null);
        if (user == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toResponse(user));
    }

    @GetMapping("/{id}/public-profile")
    public ResponseEntity<?> getPublicProfile(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null)
            return ResponseEntity.notFound().build();

        long postCount = postRepository.countByUserId(id);
        long commentCount = commentRepository.countByUserId(id);
        long catReportCount = catReportRepository.countByUserId(id);
        long squarePostCount = squarePostRepository.countByUserId(id);
        long squareCommentCount = squareCommentRepository.countByUserId(id);

        Map<String, Object> map = new HashMap<>(toResponse(user));
        map.put("createdAt", user.getCreatedAt());
        map.put("postCount", postCount + squarePostCount);
        map.put("commentCount", commentCount + squareCommentCount);
        map.put("catReportCount", catReportCount);
        // Also provide breakdown if needed, but for now aggregate is fine as per
        // request
        // "发过多少帖子，发过多少评论"
        return ResponseEntity.ok(map);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest req) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userRepository.findById(principal.getUser().getId()).orElse(null);
        if (user == null)
            return ResponseEntity.notFound().build();
        if (req.getNickname() != null)
            user.setNickname(req.getNickname());
        if (req.getAvatarUrl() != null)
            user.setAvatarUrl(req.getAvatarUrl());
        if (req.getBio() != null)
            user.setBio(req.getBio());
        user = userRepository.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    private Map<String, Object> toResponse(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "bio", user.getBio() != null ? user.getBio() : "",
                "role", user.getRole());
    }

    /**
     * List all users (role 3 admin only), for role management.
     */
    @GetMapping("/list")
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUser().getRole() < 3) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员可操作"));
        }
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * Role 3 admin grants or revokes role 2 (volunteer) for a user.
     * Cannot change role 3 users; cannot set role to 3.
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<?> setRole(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RoleChangeRequest req) {
        if (principal == null || principal.getUser().getRole() < 3) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员可操作"));
        }
        if (req.getRole() < 1 || req.getRole() > 2) {
            return ResponseEntity.badRequest().body(Map.of("message", "只能设置为1(普通用户)或2(志愿者)"));
        }
        User target = userRepository.findById(id).orElse(null);
        if (target == null)
            return ResponseEntity.notFound().build();
        if (target.getRole() >= 3) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能修改管理员角色"));
        }
        target.setRole(req.getRole());
        userRepository.save(target);
        return ResponseEntity.ok(toResponse(target));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        User user = userRepository.findById(principal.getUser().getId()).orElse(null);
        if (user == null)
            return ResponseEntity.notFound().build();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "原密码错误"));
        }
        if (req.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "新密码至少6位"));
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }

    @Data
    public static class ProfileUpdateRequest {
        @Size(max = 64)
        private String nickname;
        @Size(max = 512)
        private String avatarUrl;
        @Size(max = 500)
        private String bio;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String oldPassword;
        @NotBlank
        private String newPassword;
    }

    @Data
    public static class RoleChangeRequest {
        @NotNull
        private Integer role;
    }
}
