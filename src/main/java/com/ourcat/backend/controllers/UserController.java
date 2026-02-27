package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userRepository.findById(principal.getUser().getId()).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody ProfileUpdateRequest req) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userRepository.findById(principal.getUser().getId()).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        if (req.getBio() != null) user.setBio(req.getBio());
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
                "role", user.getRole()
        );
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
}
