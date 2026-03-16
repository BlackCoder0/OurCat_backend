package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.services.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 组织 API。见主计划「三、3.1 组织模块」接口建议。
 */
@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<?> getOrg(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        return organizationService.getOrgInfoForUser(principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<?> getMembers(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        List<OrganizationService.MemberInfo> members = organizationService.getMembers(
                id, principal.getUser().getId(), principal.getUser().getRole());
        return ResponseEntity.ok(members);
    }

    @GetMapping("/{id}/home")
    public ResponseEntity<?> getOrgHome(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        Map<String, Object> result = organizationService.getOrgHome(
                id, principal.getUser().getId(), principal.getUser().getRole());
        if (result.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("message", "无权限查看组织主页"));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        try {
            organizationService.join(principal.getUser().getId());
            return ResponseEntity.ok(Map.of("message", "申请已提交，请等待管理员审核"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/leave")
    public ResponseEntity<?> leave(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        organizationService.leave(principal.getUser().getId());
        return ResponseEntity.ok(Map.of("message", "已退出组织"));
    }

    @GetMapping("/join-requests")
    public ResponseEntity<?> listJoinRequests(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestParam(defaultValue = "1") Long orgId) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        if (principal.getUser().getRole() < 3) return ResponseEntity.status(403).body(Map.of("message", "仅管理员可查看"));
        return ResponseEntity.ok(organizationService.listPendingJoinRequests(orgId));
    }

    @PatchMapping("/join-requests/{requestId}")
    public ResponseEntity<?> reviewJoinRequest(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long requestId,
                                               @RequestBody Map<String, Boolean> body) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        if (principal.getUser().getRole() < 3) return ResponseEntity.status(403).body(Map.of("message", "仅管理员可操作"));
        Boolean approve = body != null ? body.get("approve") : null;
        if (approve == null) return ResponseEntity.badRequest().body(Map.of("message", "请提供 approve"));
        organizationService.reviewJoinRequest(requestId, approve, principal.getUser().getId());
        return ResponseEntity.ok(Map.of("message", approve ? "已通过" : "已拒绝"));
    }
}
