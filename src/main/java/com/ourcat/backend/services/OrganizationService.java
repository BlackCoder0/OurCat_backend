package com.ourcat.backend.services;

import com.ourcat.backend.models.Organization;
import com.ourcat.backend.models.OrganizationJoinRequest;
import com.ourcat.backend.models.OrganizationMember;
import com.ourcat.backend.models.RescueActivity;
import com.ourcat.backend.models.RescueTask;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.OrganizationJoinRequestRepository;
import com.ourcat.backend.repositories.OrganizationMemberRepository;
import com.ourcat.backend.repositories.OrganizationRepository;
import com.ourcat.backend.repositories.RescueActivityRepository;
import com.ourcat.backend.repositories.RescueTaskRepository;
import com.ourcat.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织服务。见主计划「三、3.1 组织模块」；补充计划「验收清单」组织/成员列表。
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final long DEFAULT_ORG_ID = 1L;
    private static final List<String> OPEN_RESCUE_STATUSES = List.of("created", "in_progress");

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationJoinRequestRepository joinRequestRepository;
    private final UserRepository userRepository;
    private final RescueActivityRepository rescueActivityRepository;
    private final RescueTaskRepository rescueTaskRepository;
    private final MessageService messageService;

    public Optional<Organization> getDefaultOrganization() {
        return organizationRepository.findById(DEFAULT_ORG_ID);
    }

    @Transactional
    public void syncVolunteerOrgMembership(Long userId, int role) {
        Optional<Organization> orgOpt = getDefaultOrganization();
        if (orgOpt.isEmpty()) {
            return;
        }
        Long orgId = orgOpt.get().getId();
        if (role >= 2) {
            if (!memberRepository.existsByOrganizationIdAndUserId(orgId, userId)) {
                OrganizationMember member = OrganizationMember.builder()
                        .organizationId(orgId)
                        .userId(userId)
                        .role("member")
                        .joinedAt(Instant.now())
                        .build();
                memberRepository.save(member);
            }
            joinRequestRepository.deleteByOrganizationIdAndUserIdAndStatus(orgId, userId, "pending");
        } else {
            memberRepository.deleteByOrganizationIdAndUserId(orgId, userId);
        }
    }

    public Optional<OrgInfo> getOrgInfoForUser(Long userId) {
        Optional<Organization> orgOpt = getDefaultOrganization();
        if (orgOpt.isEmpty())
            return Optional.empty();
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
            if (!isMember)
                return List.of();
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
        List<OrganizationMember> orgMembers = memberRepository.findByOrganizationIdOrderByJoinedAtAsc(orgId);
        List<RescueActivity> activities = rescueActivityRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        Set<Long> activityIds = activities.stream().map(RescueActivity::getId).collect(Collectors.toSet());
        Map<Long, String> activityStatusById = activities.stream()
                .collect(Collectors.toMap(RescueActivity::getId, RescueActivity::getStatus, (a, b) -> a));
        List<RescueTask> tasks = activityIds.isEmpty()
                ? List.of()
                : rescueTaskRepository.findByRescueActivityIdIn(new ArrayList<>(activityIds));
        Map<Long, Set<Long>> participatedByUser = new HashMap<>();
        Map<Long, Integer> completedTasksByUser = new HashMap<>();
        for (RescueTask task : tasks) {
            if (task.getAssigneeUserId() == null) {
                continue;
            }
            participatedByUser.computeIfAbsent(task.getAssigneeUserId(), key -> new java.util.HashSet<>())
                    .add(task.getRescueActivityId());
            boolean taskDone = "done".equalsIgnoreCase(task.getStatus());
            boolean activityCompleted = "completed".equalsIgnoreCase(activityStatusById.get(task.getRescueActivityId()));
            if (taskDone || activityCompleted) {
                completedTasksByUser.merge(task.getAssigneeUserId(), 1, Integer::sum);
            }
        }

        return orgMembers.stream()
                .map(m -> {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    String name = u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "";
                    String avatar = u != null ? u.getAvatarUrl() : null;
                    int participatedRescues = participatedByUser.getOrDefault(m.getUserId(), Set.of()).size();
                    int completedTasks = completedTasksByUser.getOrDefault(m.getUserId(), 0);
                    return new MemberInfo(m.getId(), m.getUserId(), name, avatar != null ? avatar : "", m.getRole(),
                            m.getJoinedAt(), participatedRescues, completedTasks);
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getOrgHome(Long orgId, Long requestUserId, int requestUserRole) {
        if (requestUserRole < 2 && !memberRepository.existsByOrganizationIdAndUserId(orgId, requestUserId)) {
            return Map.of();
        }

        Organization org = organizationRepository.findById(orgId).orElse(null);
        if (org == null) {
            return Map.of();
        }

        List<RescueActivity> activities = rescueActivityRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        List<RescueActivity> ongoing = activities.stream()
                .filter(a -> OPEN_RESCUE_STATUSES.contains(a.getStatus()))
                .collect(Collectors.toList());
        List<RescueActivity> completed = activities.stream()
                .filter(a -> "completed".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());

        List<MemberInfo> members = getMembers(orgId, requestUserId, requestUserRole);
        List<Map<String, Object>> activeMembers = members.stream()
                .sorted((a, b) -> Integer.compare(b.getCompletedTasks(), a.getCompletedTasks()))
                .limit(5)
                .map(m -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("userId", m.getUserId());
                    item.put("nickname", m.getNickname());
                    item.put("avatarUrl", m.getAvatarUrl());
                    item.put("participatedRescues", m.getParticipatedRescues());
                    item.put("completedTasks", m.getCompletedTasks());
                    return item;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> recentActivities = activities.stream()
                .limit(5)
                .map(a -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("activityId", a.getId());
                    item.put("title", a.getTitle());
                    item.put("status", a.getStatus());
                    item.put("urgency", a.getUrgency());
                    item.put("createdAt", a.getCreatedAt());
                    item.put("completedAt", a.getCompletedAt());
                    item.put("catId", a.getCatId());
                    item.put("squarePostId", a.getSquarePostId());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> orgInfo = new HashMap<>();
        orgInfo.put("id", org.getId());
        orgInfo.put("name", org.getName());
        orgInfo.put("description", org.getDescription());
        orgInfo.put("createdAt", org.getCreatedAt());

        Map<String, Object> stats = new HashMap<>();
        stats.put("memberCount", memberRepository.countByOrganizationId(orgId));
        stats.put("completedRescues", completed.size());
        stats.put("ongoingRescues", ongoing.size());

        Map<String, Object> result = new HashMap<>();
        result.put("orgInfo", orgInfo);
        result.put("stats", stats);
        result.put("activeMembers", activeMembers);
        result.put("recentActivities", recentActivities);
        return result;
    }

    @Transactional
    public OrganizationJoinRequest join(Long userId) {
        Optional<Organization> orgOpt = getDefaultOrganization();
        if (orgOpt.isEmpty())
            throw new IllegalStateException("未配置默认组织");
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
        if (orgOpt.isEmpty())
            return;
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
        if (req == null || !"pending".equals(req.getStatus()))
            return;

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
            if (!memberRepository.existsByOrganizationIdAndUserId(req.getOrganizationId(), req.getUserId())) {
                OrganizationMember member = OrganizationMember.builder()
                        .organizationId(req.getOrganizationId())
                        .userId(req.getUserId())
                        .role("member")
                        .build();
                memberRepository.save(member);
            }
            userRepository.findById(req.getUserId()).ifPresent(u -> {
                u.setRole(2);
                userRepository.save(u);
            });
            syncVolunteerOrgMembership(req.getUserId(), 2);
            // 发送审核通过通知
            messageService.create(req.getUserId(), "org_approved", "您的加入申请已通过，您已成为志愿者", "organization",
                    req.getOrganizationId());
        } else {
            // 发送审核拒绝通知
            messageService.create(req.getUserId(), "org_rejected", "您的加入申请被拒绝", "organization",
                    req.getOrganizationId());
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
        private int participatedRescues;
        private int completedTasks;
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
