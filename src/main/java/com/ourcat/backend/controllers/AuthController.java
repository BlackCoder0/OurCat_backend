package com.ourcat.backend.controllers;

import com.ourcat.backend.config.CaptchaConfig;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.UserRepository;
import com.ourcat.backend.utils.CaptchaStore;
import com.ourcat.backend.utils.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CaptchaStore captchaStore;
    private final java.util.Random captchaRandom;

    @GetMapping("/captcha")
    public ResponseEntity<Map<String, String>> getCaptcha() {
        CaptchaConfig.CaptchaResult result = CaptchaConfig.generate(captchaRandom);
        String key = captchaStore.put(result.code);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "image", result.imageBase64
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (!captchaStore.verify(req.getCaptchaKey(), req.getCaptcha())) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码错误"));
        }
        if (userRepository.existsByUsername(req.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名已存在"));
        }
        User user = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(1)
                .nickname(req.getNickname() != null ? req.getNickname() : req.getUsername())
                .build();
        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", toUserResponse(user)
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        if (!captchaStore.verify(req.getCaptchaKey(), req.getCaptcha())) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码错误"));
        }
        Optional<User> opt = userRepository.findByUsername(req.getUsername());
        if (opt.isEmpty() || !passwordEncoder.matches(req.getPassword(), opt.get().getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名或密码错误"));
        }
        User user = opt.get();
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", toUserResponse(user)
        ));
    }

    private Map<String, Object> toUserResponse(User user) {
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
    public static class RegisterRequest {
        @NotBlank private String username;
        @NotBlank private String password;
        private String nickname;
        @NotBlank private String captchaKey;
        @NotBlank private String captcha;
    }

    @Data
    public static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
        @NotBlank private String captchaKey;
        @NotBlank private String captcha;
    }
}
