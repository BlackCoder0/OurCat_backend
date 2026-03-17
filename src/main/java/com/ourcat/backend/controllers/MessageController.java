package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.Message;
import com.ourcat.backend.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (principal == null) return ResponseEntity.status(401).build();
        Page<Message> p = messageService.listByUser(principal.getUser().getId(), page, size);
        List<Map<String, Object>> content = p.getContent().stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("type", m.getType());
            map.put("content", m.getContent() != null ? m.getContent() : "");
            map.put("targetType", m.getTargetType());
            map.put("targetId", m.getTargetId());
            map.put("read", m.getRead());
            map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "content", content,
                "totalPages", p.getTotalPages(),
                "totalElements", p.getTotalElements()
        ));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(401).build();
        messageService.markRead(id, principal.getUser().getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/readAll")
    public ResponseEntity<Map<String, Object>> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        int updated = messageService.markAllRead(principal.getUser().getId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
