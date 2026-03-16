package com.ourcat.backend.services;

import com.ourcat.backend.models.Organization;
import com.ourcat.backend.models.OrganizationJoinRequest;
import com.ourcat.backend.models.OrganizationMember;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.OrganizationJoinRequestRepository;
import com.ourcat.backend.repositories.OrganizationMemberRepository;
import com.ourcat.backend.repositories.OrganizationRepository;
import com.ourcat.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 组织服务。见主计划「三、3.1 组织模块」；补充计划「验收清单」组织/成员列表。
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final long DEFAULT_ORG_ID = 1L;

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationJoinRequestRepository joinRequestRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;

    public Optional<Organization> getDefaultOrganization() {
        return organizationRepository.findById(DEFAULT_ORG_ID);
    }

    public Optional<OrgInfo> getOrgInfoForUser(Long userId) {
        Optional<Organization> orgOpt = getDefaultOrganization();
        if (orgOpt.isEmpty()) return Optional.empty();
        Organization org = orgOpt.get();
        boolean isMember = memberRepository.existsByOrganizationIdAndUserId(org.getId(), userId);
        boolean hasPendingRequest = joinRequestRepository.existsByOrganizationIdAndUserIdAndStatus(
                org.getId(), userId, "pending");
        return Optional.of(OrgInfo.builder()
                .id(org.getId())
                .name(org.getName())
                .description(org.getDescription())
                .createdAt(org.getCreatedAt())
                .isMember(isMember)
                .hasPendingRequest(hasPendingRequest)
                .build());
    }

    public List<MemberInfo> getMembers(Long orgId, Long requestUserId, int requestUserRole) {
        if (requestUserRole < 2) {
            boolean isMember = memberRepository.existsByOrganizationIdAndUserId(orgId, requestUserId);
            if (!isMember) return List.of();
        }
        // 3级管理员自动获得组织身份（如果尚未加入）
        if (requestUserRole >= 3 && !memberRepository.existsByOrganizationIdAndUserId(orgId, requestUserId)) {
            User u = userRepository.findById(requestUserId).orElse(null);
            if (u != null) {
                OrganizationMember adminMember = OrganizationMember.builder()
                        .organizationId(orgId)
                        .userId(requestUserId)
                        .role("admin")
                        .joinedAt(Instant.now())
                        .build();
                memberRepository.save(adminMember);
            }
        }
        return memberRepository.findByOrganizationIdOrderByJoinedAtAsc(orgId).stream()
                .map(m -> {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    String name = u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "";
                    String avatar = u != null ? u.getAvatarUrl() : null;
                    return new MemberInfo(m.getId(), m.getUserId(), name, avatar != null ? avatar : "", m.getRole(), m.getJoinedAt());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public OrganizationJoinRequest join(Long userId) {
        Optional<Organization> orgOpt = getDefaultOrganization();
        if (orgOpt.isEmpty()) throw new IllegalStateException("未配置默认组织");
        Organization org = orgOpt.get();
        if (memberRepository.existsByOrganizationIdAndUserId(org.getId(), userId)) {
            throw new IllegalStateException("您已是组织成员");
        }
        if (joinRequestRepository.existsByOrganizationIdAndUserIdAndStatus(org.getId(), userId, "pending")) {
            throw new IllegalStateException("您已提交过申请，请等待审核");
        }
        OrganizationJoinRequest req = OrganizationJoinRequest.builder()
                .organizationId(org.getId())
                .userId(userId)
                .status("pending")
                .build();
        return joinRequestRepository.save(req);
    }

    @Transactional
    public void leave(Long userId) {
        Optional<Organization> orgOpt = getDefaultOrganization();
        if (orgOpt.isEmpty()) return;
        memberRepository.deleteByOrganizationIdAndUserId(orgOpt.get().getId(), userId);
        userRepository.findById(userId).ifPresent(u -> {
            if (u.getRole() == 2) {
                u.setRole(1);
                userRepository.save(u);
            }
        });
    }

    public List<JoinRequestInfo> listPendingJoinRequests(Long orgId) {
        return joinRequestRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, "pending").stream()
                .map(req -> {
                    User u = userRepository.findById(req.getUserId()).orElse(null);
                    String name = u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "";
                    return new JoinRequestInfo(req.getId(), req.getUserId(), name, req.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void reviewJoinRequest(Long requestId, boolean approve, Long adminUserId) {
        OrganizationJoinRequest req = joinRequestRepository.findById(requestId).orElse(null);
        if (req == null || !"pending".equals(req.getStatus())) return;

        // 3级管理员自动获得组织身份（如果尚未加入）
        if (!memberRepository.existsByOrganizationIdAndUserId(req.getOrganizationId(), adminUserId)) {
            User admin = userRepository.findById(adminUserId).orElse(null);
            if (admin != null && admin.getRole() >= 3) {
                OrganizationMember adminMember = OrganizationMember.builder()
                        .organizationId(req.getOrganizationId())
                        .userId(adminUserId)
                        .role("admin")
                        .joinedAt(Instant.now())
                        .build();
                memberRepository.save(adminMember);
            }
        }

        req.setStatus(approve ? "approved" : "rejected");
        req.setReviewedAt(Instant.now());
        req.setReviewedBy(adminUserId);
        joinRequestRepository.save(req);
        if (approve) {
            OrganizationMember member = OrganizationMember.builder()
                    .organizationId(req.getOrganizationId())
                    .userId(req.getUserId())
                    .role("member")
                    .build();
            memberRepository.save(member);
            userRepository.findById(req.getUserId()).ifPresent(u -> {
                u.setRole(2);
                userRepository.save(u);
            });
            // 发送审核通过通知
            messageService.create(req.getUserId(), "org_approved", "您的加入申请已通过，您已成为志愿者", "organization", req.getOrganizationId());
        } else {
            // 发送审核拒绝通知
            messageService.create(req.getUserId(), "org_rejected", "您的加入申请被拒绝", "organization", req.getOrganizationId());
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OrgInfo {
        private Long id;
        private String name;
        private String description;
        private Instant createdAt;
        private boolean isMember;
        private boolean hasPendingRequest;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MemberInfo {
        private Long memberId;
        private Long userId;
        private String nickname;
        private String avatarUrl;
        private String role;
        private Instant joinedAt;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class JoinRequestInfo {
        private Long id;
        private Long userId;
        private String nickname;
        private Instant createdAt;
    }
}
