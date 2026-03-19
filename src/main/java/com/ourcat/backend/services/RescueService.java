package com.ourcat.backend.services;

import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.CatReport;
import com.ourcat.backend.models.RescueActivity;
import com.ourcat.backend.models.RescueTask;
import com.ourcat.backend.models.RescueTaskLog;
import com.ourcat.backend.models.SquarePost;
import com.ourcat.backend.repositories.CatReportRepository;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.OrganizationMemberRepository;
import com.ourcat.backend.repositories.RescueActivityRepository;
import com.ourcat.backend.repositories.RescueTaskLogRepository;
import com.ourcat.backend.repositories.RescueTaskRepository;
import com.ourcat.backend.repositories.SquarePostRepository;
import com.ourcat.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RescueService {

    private static final long DEFAULT_ORG_ID = 1L;
    private static final List<String> OPEN_STATUSES = List.of("created", "in_progress");
    private static final List<String> PROBLEM_TYPES = List.of("受伤", "疾病", "困境", "其他");
    private static final List<String> CAT_STATUSES_PROTECTED_FROM_AUTO_SYNC = List.of("收养", "已故");

    private final RescueActivityRepository rescueActivityRepository;
    private final RescueTaskRepository rescueTaskRepository;
    private final CatRepository catRepository;
    private final CatReportRepository catReportRepository;
    private final SquarePostRepository squarePostRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RescueTaskLogRepository rescueTaskLogRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;

    @Transactional
    public RescueActivity create(Long userId, String title, String description, Long catId, Long squarePostId,
            String urgency, String problemType) {
        if ((catId == null || catId <= 0) && (squarePostId == null || squarePostId <= 0)) {
            throw new IllegalArgumentException("请关联猫咪或广场救助帖");
        }
        Long finalCatId = catId != null && catId > 0 ? catId : null;
        Long finalSquarePostId = squarePostId != null && squarePostId > 0 ? squarePostId : null;
        if (finalCatId != null
                && !rescueActivityRepository.findByCatIdAndStatusInOrderByCreatedAtDesc(finalCatId, OPEN_STATUSES)
                        .isEmpty()) {
            throw new IllegalStateException("当前处于待救助状态");
        }
        String normalizedProblemType = normalizeProblemType(problemType);
        RescueActivity activity = RescueActivity.builder()
                .title(title != null ? title : "救助")
                .description(description)
                .catId(finalCatId)
                .squarePostId(finalSquarePostId)
                .organizationId(DEFAULT_ORG_ID)
                .urgency(urgency != null && "urgent".equalsIgnoreCase(urgency) ? "urgent" : "normal")
                .problemType(normalizedProblemType)
                .status("created")
                .createdBy(userId)
                .build();
        RescueActivity saved = rescueActivityRepository.save(activity);
        syncCatStatusFromProblemType(finalCatId, normalizedProblemType);
        return saved;
    }

    public Page<RescueActivity> list(int page, int size, String status, String urgency) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isEmpty()) {
            return rescueActivityRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        Page<RescueActivity> all = rescueActivityRepository.findAllByOrderByCreatedAtDesc(pageable);
        if (urgency != null && "urgent".equalsIgnoreCase(urgency)) {
            List<RescueActivity> content = all.getContent().stream()
                    .filter(a -> "urgent".equalsIgnoreCase(a.getUrgency()))
                    .collect(Collectors.toList());
            return new PageImpl<>(content, pageable, all.getTotalElements());
        }
        return all;
    }

    public Optional<RescueActivity> getById(Long id) {
        return rescueActivityRepository.findById(id);
    }

    @Transactional
    public Optional<RescueActivity> updateStatus(Long id, String status, Long operatorUserId) {
        return rescueActivityRepository.findById(id).map(activity -> {
            activity.setStatus(status);
            if ("completed".equals(status)) {
                activity.setCompletedAt(Instant.now());
            }
            RescueActivity saved = rescueActivityRepository.save(activity);
            if ("completed".equals(status)) {
                syncCatStatusAfterCompleted(saved.getCatId());
            }
            if (operatorUserId != null && !operatorUserId.equals(activity.getCreatedBy())) {
                String statusText;
                if ("created".equals(status)) {
                    statusText = "已创建";
                } else if ("in_progress".equals(status)) {
                    statusText = "进行中";
                } else {
                    statusText = "已完成";
                }
                messageService.create(
                        activity.getCreatedBy(),
                        "rescue_status_changed",
                        "您的救助活动《" + activity.getTitle() + "》状态已变更为：" + statusText,
                        "rescue_activity",
                        id);
            }
            return saved;
        });
    }

    private String normalizeProblemType(String problemType) {
        if (problemType == null) {
            return null;
        }
        String normalized = problemType.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if ("生病".equals(normalized)) {
            return "疾病";
        }
        if (PROBLEM_TYPES.contains(normalized)) {
            return normalized;
        }
        return "其他";
    }

    private void syncCatStatusFromProblemType(Long catId, String problemType) {
        if (catId == null || problemType == null) {
            return;
        }
        catRepository.findById(catId).ifPresent(cat -> {
            String currentStatus = cat.getStatus();
            if (currentStatus != null && CAT_STATUSES_PROTECTED_FROM_AUTO_SYNC.contains(currentStatus)) {
                return;
            }
            cat.setStatus(problemType);
            catRepository.save(cat);
        });
    }

    private void syncCatStatusAfterCompleted(Long catId) {
        if (catId == null) {
            return;
        }
        catRepository.findById(catId).ifPresent(cat -> {
            String currentStatus = cat.getStatus();
            if (currentStatus != null && CAT_STATUSES_PROTECTED_FROM_AUTO_SYNC.contains(currentStatus)) {
                return;
            }
            if (currentStatus != null && PROBLEM_TYPES.contains(currentStatus)) {
                cat.setStatus("活跃");
                catRepository.save(cat);
            }
        });
    }

    public List<CatNeedRescueDto> getCatsNeedRescue() {
        List<RescueActivity> open = rescueActivityRepository
                .findByCatIdNotNullAndStatusInOrderByCreatedAtDesc(OPEN_STATUSES);
        List<CatNeedRescueDto> result = new ArrayList<>();
        for (RescueActivity activity : open) {
            Optional<CatReport> latestReport = catReportRepository
                    .findFirstByCatIdOrderByReportTimeDesc(activity.getCatId());
            if (latestReport.isEmpty()) {
                continue;
            }
            CatReport report = latestReport.get();
            Cat cat = catRepository.findById(activity.getCatId()).orElse(null);
            String catName = cat != null && cat.getName() != null ? cat.getName() : "未命名";
            result.add(new CatNeedRescueDto(
                    activity.getCatId(),
                    activity.getId(),
                    activity.getTitle(),
                    activity.getUrgency(),
                    report.getLat(),
                    report.getLng(),
                    catName,
                    report.getImageUrl() != null ? report.getImageUrl() : ""));
        }
        return result;
    }

    public List<RescueLocationDto> getRescueLocations() {
        List<RescueLocationDto> result = new ArrayList<>();

        List<RescueActivity> catActivities = rescueActivityRepository
                .findByCatIdNotNullAndStatusInOrderByCreatedAtDesc(OPEN_STATUSES);
        for (RescueActivity activity : catActivities) {
            Optional<CatReport> latestReport = catReportRepository
                    .findFirstByCatIdOrderByReportTimeDesc(activity.getCatId());
            if (latestReport.isEmpty()) {
                continue;
            }
            CatReport report = latestReport.get();
            if (report.getLat() != null && report.getLng() != null) {
                result.add(new RescueLocationDto(
                        activity.getId(),
                        activity.getTitle(),
                        activity.getUrgency(),
                        activity.getStatus(),
                        report.getLat(),
                        report.getLng(),
                        activity.getCatId(),
                        activity.getSquarePostId()));
            }
        }

        List<RescueActivity> postActivities = rescueActivityRepository
                .findBySquarePostIdNotNullAndStatusInOrderByCreatedAtDesc(OPEN_STATUSES);
        for (RescueActivity activity : postActivities) {
            Optional<SquarePost> postOpt = squarePostRepository.findById(activity.getSquarePostId());
            if (postOpt.isEmpty()) {
                continue;
            }
            SquarePost post = postOpt.get();
            if (post.getLocation() == null || post.getLocation().isEmpty()) {
                continue;
            }
            String[] parts = post.getLocation().split(",");
            if (parts.length < 2) {
                continue;
            }
            try {
                String latStr = parts[0].trim();
                String lngStr = parts[1].trim().split("\\s+")[0];
                double lat = Double.parseDouble(latStr);
                double lng = Double.parseDouble(lngStr);
                result.add(new RescueLocationDto(
                        activity.getId(),
                        activity.getTitle(),
                        activity.getUrgency(),
                        activity.getStatus(),
                        lat,
                        lng,
                        activity.getCatId(),
                        activity.getSquarePostId()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public Map<String, Object> toDetailMap(RescueActivity activity, com.ourcat.backend.models.User creator) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", activity.getId());
        result.put("title", activity.getTitle());
        result.put("description", activity.getDescription());
        result.put("catId", activity.getCatId());
        result.put("squarePostId", activity.getSquarePostId());
        result.put("organizationId", activity.getOrganizationId());
        result.put("urgency", activity.getUrgency());
        result.put("problemType", activity.getProblemType());
        result.put("status", activity.getStatus());
        result.put("createdBy", activity.getCreatedBy());
        result.put("createdAt", activity.getCreatedAt());
        result.put("completedAt", activity.getCompletedAt());
        result.put("updatedAt", activity.getCompletedAt());
        if (creator != null) {
            result.put("creatorName", creator.getNickname() != null ? creator.getNickname() : creator.getUsername());
            result.put("creatorAvatar", creator.getAvatarUrl() != null ? creator.getAvatarUrl() : "");
            result.put("createdByName", creator.getNickname() != null ? creator.getNickname() : creator.getUsername());
        }
        if (activity.getCatId() != null) {
            catRepository.findById(activity.getCatId()).ifPresent(cat -> result.put("cat", toCatRef(cat)));
            catReportRepository.findFirstByCatIdOrderByReportTimeDesc(activity.getCatId()).ifPresent(report -> {
                if (report.getLat() != null && report.getLng() != null) {
                    result.put("lat", report.getLat());
                    result.put("lng", report.getLng());
                }
            });
        }
        if (activity.getSquarePostId() != null) {
            squarePostRepository.findById(activity.getSquarePostId()).ifPresent(post -> {
                String text = post.getText() != null ? post.getText() : "";
                result.put("squarePostTitle", text.length() > 50 ? text.substring(0, 50) + "..." : text);
                result.put("squarePostLocation", post.getLocation() != null ? post.getLocation() : "");
            });
        }
        List<RescueTask> tasks = rescueTaskRepository.findByRescueActivityIdOrderByAssignedAtAsc(activity.getId());
        result.put("tasks", tasks.stream().map(this::taskToMap).collect(Collectors.toList()));
        return result;
    }

    public List<RescueTask> getTasksByActivityId(Long activityId) {
        return rescueTaskRepository.findByRescueActivityIdOrderByAssignedAtAsc(activityId);
    }

    @Transactional
    public RescueTask assignTask(Long activityId, Long assigneeUserId, Long assignerUserId) {
        RescueActivity activity = rescueActivityRepository.findById(activityId).orElse(null);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        if ("completed".equalsIgnoreCase(activity.getStatus())) {
            throw new IllegalArgumentException("活动已完成，无法指派任务");
        }
        if (!activity.getCreatedBy().equals(assignerUserId)) {
            boolean isManager = organizationMemberRepository
                    .findByOrganizationIdAndUserId(activity.getOrganizationId(), assignerUserId)
                    .map(member -> "manager".equals(member.getRole()))
                    .orElse(false);
            if (!isManager) {
                throw new IllegalArgumentException("仅活动创建者或组织管理员可指派");
            }
        }
        if (!organizationMemberRepository.existsByOrganizationIdAndUserId(activity.getOrganizationId(),
                assigneeUserId)) {
            throw new IllegalArgumentException("只能指派组织成员");
        }
        if (rescueTaskRepository.existsByRescueActivityIdAndAssigneeUserId(activityId, assigneeUserId)) {
            throw new IllegalArgumentException("该成员已拥有该救助活动任务");
        }
        RescueTask task = RescueTask.builder()
                .rescueActivityId(activityId)
                .assigneeUserId(assigneeUserId)
                .assignerUserId(assignerUserId)
                .status("assigned")
                .build();
        task = rescueTaskRepository.save(task);
        createTaskLog(task.getId(), assignerUserId, "system", "任务已指派给成员", null);
        messageService.create(assigneeUserId, "rescue_task_assigned", "您有一个新的救助任务：" + activity.getTitle(),
                "rescue_activity", activityId);
        return task;
    }

    @Transactional
    public RescueTask claimTask(Long activityId, Long userId) {
        RescueActivity activity = rescueActivityRepository.findById(activityId).orElse(null);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        if ("completed".equalsIgnoreCase(activity.getStatus())) {
            throw new IllegalArgumentException("活动已完成，无法申领任务");
        }
        if (!organizationMemberRepository.existsByOrganizationIdAndUserId(activity.getOrganizationId(), userId)) {
            throw new IllegalArgumentException("仅组织成员可申领任务");
        }
        if (rescueTaskRepository.existsByRescueActivityIdAndAssigneeUserId(activityId, userId)) {
            throw new IllegalArgumentException("您已申领/被指派过该救助活动任务");
        }
        RescueTask task = RescueTask.builder()
                .rescueActivityId(activityId)
                .assigneeUserId(userId)
                .assignerUserId(null)
                .status("assigned")
                .build();
        task = rescueTaskRepository.save(task);
        createTaskLog(task.getId(), userId, "system", "成员已申领任务", null);
        messageService.create(userId, "rescue_task_claimed", "您已申领救助任务：" + activity.getTitle(), "rescue_activity",
                activityId);
        return task;
    }

    public List<RescueTask> getMyTasks(Long userId) {
        return rescueTaskRepository.findByAssigneeUserIdOrderByAssignedAtDesc(userId);
    }

    @Transactional
    public int markTasksDoneForActivity(Long activityId, Long operatorId) {
        if (activityId == null || activityId <= 0) {
            return 0;
        }
        int updated = 0;
        List<RescueTask> tasks = rescueTaskRepository.findByRescueActivityIdOrderByAssignedAtAsc(activityId);
        for (RescueTask task : tasks) {
            if (task == null) {
                continue;
            }
            String status = task.getStatus() != null ? task.getStatus() : "";
            if ("done".equalsIgnoreCase(status)) {
                continue;
            }
            task.setStatus("done");
            task.setCompletedAt(Instant.now());
            rescueTaskRepository.save(task);
            createTaskLog(task.getId(), operatorId, "system", "事件已解决", null);
            updated++;
        }
        return updated;
    }

    @Transactional
    public Optional<RescueTask> updateTask(Long taskId, Long userId, String status, String completionNote,
            String completionImages) {
        return rescueTaskRepository.findById(taskId)
                .filter(task -> task.getAssigneeUserId() != null && task.getAssigneeUserId().equals(userId))
                .map(task -> {
                    if (status != null) {
                        task.setStatus(status);
                    }
                    if (completionNote != null) {
                        task.setCompletionNote(completionNote);
                    }
                    if (completionImages != null) {
                        task.setCompletionImages(completionImages);
                    }
                    if ("done".equals(status)) {
                        task.setCompletedAt(Instant.now());
                    }
                    RescueTask saved = rescueTaskRepository.save(task);
                    if (status != null && !status.isEmpty()) {
                        createTaskLog(saved.getId(), userId, "status", "任务状态更新为：" + status, null);
                    }
                    if (completionNote != null && !completionNote.isEmpty()) {
                        createTaskLog(saved.getId(), userId, "completion", completionNote, completionImages);
                    }
                    return saved;
                });
    }

    public List<Map<String, Object>> getTaskLogs(Long taskId, Long userId) {
        RescueTask task = rescueTaskRepository.findById(taskId).orElse(null);
        if (task == null || !canViewTask(task, userId)) {
            return List.of();
        }
        return rescueTaskLogRepository.findByRescueTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::taskLogToMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<Map<String, Object>> addTaskLog(Long taskId, Long userId, String content, String images,
            String logType) {
        RescueTask task = rescueTaskRepository.findById(taskId).orElse(null);
        if (task == null || task.getAssigneeUserId() == null || !task.getAssigneeUserId().equals(userId)) {
            return Optional.empty();
        }
        String text = content != null ? content.trim() : "";
        if (text.isEmpty()) {
            throw new IllegalArgumentException("日志内容不能为空");
        }
        RescueTaskLog saved = createTaskLog(taskId, userId, normalizeLogType(logType), text, images);
        return Optional.of(taskLogToMap(saved));
    }

    public Map<String, Object> taskToMap(RescueTask task) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", task.getId());
        result.put("rescueActivityId", task.getRescueActivityId());
        result.put("assigneeUserId", task.getAssigneeUserId());
        result.put("assignerUserId", task.getAssignerUserId());
        result.put("status", task.getStatus());
        result.put("assignedAt", task.getAssignedAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("completionNote", task.getCompletionNote());
        result.put("completionImages", task.getCompletionImages());
        if (task.getRescueActivityId() != null) {
            rescueActivityRepository.findById(task.getRescueActivityId()).ifPresent(activity -> {
                result.put("rescueActivityTitle", activity.getTitle() != null ? activity.getTitle() : "");
                result.put("rescueActivityUrgency", activity.getUrgency() != null ? activity.getUrgency() : "normal");
                result.put("rescueActivityStatus", activity.getStatus() != null ? activity.getStatus() : "created");
                result.put("squarePostId", activity.getSquarePostId() != null ? activity.getSquarePostId() : 0L);
                if (activity.getCatId() != null && activity.getCatId() > 0) {
                    catRepository.findById(activity.getCatId()).ifPresent(cat -> {
                        result.put("catId", cat.getId());
                        result.put("catName", cat.getName() != null ? cat.getName() : "");
                        result.put("catImageUrl", cat.getPrimaryImageUrl() != null ? cat.getPrimaryImageUrl() : "");
                    });
                }
                if (activity.getSquarePostId() != null && activity.getSquarePostId() > 0) {
                    squarePostRepository.findById(activity.getSquarePostId()).ifPresent(post -> {
                        result.put("squarePostStatus", post.getStatus() != null ? post.getStatus() : "open");
                        result.put("squarePostType", post.getType() != null ? post.getType() : "");
                        result.put("squarePostLocation", post.getLocation() != null ? post.getLocation() : "");
                        result.put("squarePostSnippet", buildSnippet(post.getText()));
                    });
                }
            });
        }
        if (task.getAssigneeUserId() != null) {
            userRepository.findById(task.getAssigneeUserId()).ifPresent(u -> {
                result.put("assigneeName", u.getNickname() != null ? u.getNickname() : u.getUsername());
                result.put("assigneeAvatar", u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
            });
        }
        if (task.getAssignerUserId() != null) {
            userRepository.findById(task.getAssignerUserId()).ifPresent(u -> {
                result.put("assignerName", u.getNickname() != null ? u.getNickname() : u.getUsername());
                result.put("assignerAvatar", u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
            });
        }
        return result;
    }

    private String buildSnippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return "";
        }
        return t.length() > 26 ? t.substring(0, 26) + "..." : t;
    }

    private RescueTaskLog createTaskLog(Long taskId, Long userId, String logType, String content, String images) {
        RescueTaskLog log = RescueTaskLog.builder()
                .rescueTaskId(taskId)
                .userId(userId)
                .logType(normalizeLogType(logType))
                .content(content)
                .images(images != null && !images.isEmpty() ? images : null)
                .build();
        return rescueTaskLogRepository.save(log);
    }

    private String normalizeLogType(String logType) {
        if (logType == null || logType.isEmpty()) {
            return "progress";
        }
        if (List.of("progress", "status", "completion", "system").contains(logType)) {
            return logType;
        }
        return "progress";
    }

    private boolean canViewTask(RescueTask task, Long userId) {
        if (userId == null) {
            return false;
        }
        if (task.getAssigneeUserId() != null && task.getAssigneeUserId().equals(userId)) {
            return true;
        }
        RescueActivity activity = rescueActivityRepository.findById(task.getRescueActivityId()).orElse(null);
        if (activity == null) {
            return false;
        }
        if (activity.getCreatedBy() != null && activity.getCreatedBy().equals(userId)) {
            return true;
        }
        return organizationMemberRepository.existsByOrganizationIdAndUserId(activity.getOrganizationId(), userId);
    }

    private Map<String, Object> taskLogToMap(RescueTaskLog log) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", log.getId());
        result.put("rescueTaskId", log.getRescueTaskId());
        result.put("userId", log.getUserId());
        result.put("logType", log.getLogType());
        result.put("content", log.getContent());
        result.put("images", log.getImages());
        result.put("createdAt", log.getCreatedAt());
        if (log.getUserId() != null) {
            userRepository.findById(log.getUserId()).ifPresent(user -> {
                result.put("userName", user.getNickname() != null ? user.getNickname() : user.getUsername());
                result.put("userAvatar", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
            });
        }
        return result;
    }

    private Map<String, Object> toCatRef(Cat cat) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", cat.getId());
        result.put("name", cat.getName());
        result.put("color", cat.getColor());
        result.put("status", cat.getStatus());
        result.put("primaryImageUrl", cat.getPrimaryImageUrl());
        return result;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CatNeedRescueDto {
        private Long catId;
        private Long activityId;
        private String title;
        private String urgency;
        private double lat;
        private double lng;
        private String catName;
        private String imageUrl;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RescueLocationDto {
        private Long activityId;
        private String title;
        private String urgency;
        private String status;
        private double lat;
        private double lng;
        private Long catId;
        private Long squarePostId;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalActivities = rescueActivityRepository.count();
        long completedActivities = rescueActivityRepository.countByStatus("completed");
        long inProgressActivities = rescueActivityRepository.countByStatusIn(OPEN_STATUSES);

        stats.put("totalActivities", totalActivities);
        stats.put("completedActivities", completedActivities);
        stats.put("inProgressActivities", inProgressActivities);

        List<Map<String, Object>> topContributors = getTopContributors();
        stats.put("topContributors", topContributors);

        stats.put("problemTypeDistribution", getProblemTypeDistribution());
        stats.put("monthlyTrendAll", getMonthlyTrend(6, false));
        stats.put("monthlyTrendCompleted", getMonthlyTrend(6, true));

        return stats;
    }

    private List<Map<String, Object>> getProblemTypeDistribution() {
        Map<String, Long> countByType = new HashMap<>();
        countByType.put("受伤", 0L);
        countByType.put("疾病", 0L);
        countByType.put("困境", 0L);
        countByType.put("其他", 0L);

        for (RescueActivity a : rescueActivityRepository.findAll()) {
            String t = a.getProblemType() != null ? a.getProblemType().trim() : "";
            if (t.isEmpty()) {
                countByType.merge("其他", 1L, Long::sum);
                continue;
            }
            if ("生病".equals(t)) {
                t = "疾病";
            }
            if (!countByType.containsKey(t)) {
                t = "其他";
            }
            countByType.merge(t, 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : List.of("受伤", "疾病", "困境", "其他")) {
            result.add(Map.of("type", key, "count", countByType.getOrDefault(key, 0L)));
        }
        return result;
    }

    private List<Map<String, Object>> getMonthlyTrend(int months, boolean completedOnly) {
        int safeMonths = Math.max(1, Math.min(months, 24));
        ZoneId zone = ZoneId.systemDefault();
        YearMonth current = YearMonth.now(zone);

        Map<YearMonth, Long> countByMonth = new HashMap<>();
        for (int i = 0; i < safeMonths; i++) {
            countByMonth.put(current.minusMonths(i), 0L);
        }
        for (RescueActivity a : rescueActivityRepository.findAll()) {
            Instant time = completedOnly ? a.getCompletedAt() : a.getCreatedAt();
            if (completedOnly) {
                if (!"completed".equalsIgnoreCase(a.getStatus()) || time == null) {
                    continue;
                }
            } else {
                if (time == null) {
                    continue;
                }
            }
            YearMonth m = YearMonth.from(time.atZone(zone));
            if (countByMonth.containsKey(m)) {
                countByMonth.merge(m, 1L, Long::sum);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = safeMonths - 1; i >= 0; i--) {
            YearMonth m = current.minusMonths(i);
            result.add(Map.of("month", m.toString(), "count", countByMonth.getOrDefault(m, 0L)));
        }
        return result;
    }

    private List<Map<String, Object>> getTopContributors() {
        List<RescueTask> allTasks = rescueTaskRepository.findAll();
        Map<Long, Long> contributorCount = new HashMap<>();

        for (RescueTask task : allTasks) {
            if (task.getAssigneeUserId() != null) {
                contributorCount.merge(task.getAssigneeUserId(), 1L, Long::sum);
            }
        }

        return contributorCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("userId", entry.getKey());
                    item.put("taskCount", entry.getValue());
                    item.put("count", entry.getValue());
                    userRepository.findById(entry.getKey()).ifPresent(user -> {
                        item.put("username", user.getUsername());
                        item.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                        item.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                    });
                    return item;
                })
                .collect(Collectors.toList());
    }
}
