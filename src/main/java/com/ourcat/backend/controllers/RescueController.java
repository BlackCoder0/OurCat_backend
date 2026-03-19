package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.RescueActivity;
import com.ourcat.backend.models.RescueTask;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.RescueContactRepository;
import com.ourcat.backend.repositories.RescueGuideRepository;
import com.ourcat.backend.repositories.RescueArticleRepository;
import com.ourcat.backend.repositories.UserRepository;
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
import java.util.stream.Collectors;

/**
 * 救助活动 API。见主计划「三、3.2 救助活动」「三、3.4 需救助猫在地图体现」。
 */
@RestController
@RequestMapping("/api/rescue")
@RequiredArgsConstructor
public class RescueController {

    private final RescueService rescueService;
    private final SquareService squareService;
    private final UserRepository userRepository;
    private final RescueGuideRepository rescueGuideRepository;
    private final RescueContactRepository rescueContactRepository;
    private final RescueArticleRepository rescueArticleRepository;

    @PostMapping("/activities")
    public ResponseEntity<?> createActivity(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRescueActivityRequest req) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        try {
            RescueActivity activity = rescueService.create(
                    principal.getUser().getId(),
                    req.getTitle(), req.getDescription(),
                    req.getCatId(), req.getSquarePostId(),
                    req.getUrgency(), req.getProblemType());

            // 同步创建广场救助广播
            if (activity.getId() != null && (req.getSquarePostId() == null || req.getSquarePostId() <= 0)) {
                try {
                    String postText = req.getTitle();
                    if (req.getDescription() != null && !req.getDescription().isEmpty()) {
                        postText = req.getTitle() + "\n\n" + req.getDescription();
                    }
                    String location = null;
                    Double lat = req.getLatitude();
                    Double lng = req.getLongitude();
                    String locationName = req.getLocationName();
                    if (lat != null && lng != null) {
                        String base = lat + "," + lng;
                        String name = locationName != null ? locationName.trim() : "";
                        location = name.isEmpty() ? base : base + " " + name;
                    } else if (locationName != null && !locationName.trim().isEmpty()) {
                        location = locationName.trim();
                    } else if (req.getCatId() != null && req.getCatId() > 0) {
                        location = squareService.getCatLocationForPost(req.getCatId());
                    }
                    squareService.createPostFromRescue(
                            principal.getUser().getId(),
                            postText,
                            activity.getId(),
                            req.getCatId(),
                            location);
                } catch (Exception e) {
                    // 广播创建失败不影响救助活动
                }
            }

            RescueActivity responseActivity = rescueService.getById(activity.getId()).orElse(activity);
            return ResponseEntity.ok(toMap(responseActivity));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("message", "当前处于待救助状态"));
        }
    }

    @GetMapping("/activities")
    public ResponseEntity<?> listActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String urgency) {
        Page<RescueActivity> p = rescueService.list(page, size, status, urgency);
        List<Map<String, Object>> content = p.getContent().stream().map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "content", content,
                "totalPages", p.getTotalPages(),
                "totalElements", p.getTotalElements()));
    }

    @GetMapping("/activities/{id}")
    public ResponseEntity<?> getActivity(@PathVariable Long id) {
        return rescueService.getById(id)
                .map(activity -> {
                    User creator = userRepository.findById(activity.getCreatedBy()).orElse(null);
                    return ResponseEntity.<Object>ok(rescueService.toDetailMap(activity, creator));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/activities/{id}")
    public ResponseEntity<?> updateActivityStatus(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        String status = body != null ? body.get("status") : null;
        if (status == null || !List.of("created", "in_progress", "completed").contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("message", "status 须为 created / in_progress / completed"));
        }
        return rescueService.updateStatus(id, status, principal.getUser().getId())
                .map(a -> ResponseEntity.ok(toMap(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/activities/{activityId}/tasks")
    public ResponseEntity<?> listActivityTasks(@PathVariable Long activityId) {
        List<RescueTask> tasks = rescueService.getTasksByActivityId(activityId);
        List<Map<String, Object>> list = tasks.stream()
                .map(rescueService::taskToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/activities/{activityId}/tasks")
    public ResponseEntity<?> assignTask(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long activityId,
            @RequestBody Map<String, Long> body) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        Long assigneeUserId = body != null ? body.get("assigneeUserId") : null;
        if (assigneeUserId == null)
            return ResponseEntity.badRequest().body(Map.of("message", "请提供 assigneeUserId"));
        try {
            RescueTask task = rescueService.assignTask(activityId, assigneeUserId, principal.getUser().getId());
            return ResponseEntity.ok(rescueService.taskToMap(task));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/activities/{activityId}/tasks/claim")
    public ResponseEntity<?> claimTask(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long activityId) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        try {
            RescueTask task = rescueService.claimTask(activityId, principal.getUser().getId());
            return ResponseEntity.ok(rescueService.taskToMap(task));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<?> myTasks(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        List<RescueTask> tasks = rescueService.getMyTasks(principal.getUser().getId());
        List<Map<String, Object>> list = tasks.stream().map(rescueService::taskToMap).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/tasks/{taskId}")
    public ResponseEntity<?> updateTask(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        String status = body != null ? body.get("status") : null;
        String completionNote = body != null ? body.get("completionNote") : null;
        String completionImages = body != null ? body.get("completionImages") : null;
        return rescueService.updateTask(taskId, principal.getUser().getId(), status, completionNote, completionImages)
                .map(t -> ResponseEntity.ok(rescueService.taskToMap(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{taskId}/logs")
    public ResponseEntity<?> listTaskLogs(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        return ResponseEntity.ok(rescueService.getTaskLogs(taskId, principal.getUser().getId()));
    }

    @PostMapping("/tasks/{taskId}/logs")
    public ResponseEntity<?> addTaskLog(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> body) {
        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        String content = body != null ? body.get("content") : null;
        String images = body != null ? body.get("images") : null;
        String logType = body != null ? body.get("logType") : null;
        try {
            return rescueService.addTaskLog(taskId, principal.getUser().getId(), content, images, logType)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(403).body(Map.of("message", "仅任务执行人可记录日志")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/cats-need-rescue")
    public ResponseEntity<List<Map<String, Object>>> catsNeedRescue() {
        List<RescueService.CatNeedRescueDto> list = rescueService.getCatsNeedRescue();
        List<Map<String, Object>> result = list.stream().map(d -> Map.<String, Object>of(
                "catId", d.getCatId(),
                "activityId", d.getActivityId(),
                "title", d.getTitle() != null ? d.getTitle() : "",
                "urgency", d.getUrgency() != null ? d.getUrgency() : "normal",
                "lat", d.getLat(),
                "lng", d.getLng(),
                "catName", d.getCatName() != null ? d.getCatName() : "",
                "imageUrl", d.getImageUrl() != null ? d.getImageUrl() : "")).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rescue-locations")
    public ResponseEntity<List<Map<String, Object>>> rescueLocations() {
        List<RescueService.RescueLocationDto> list = rescueService.getRescueLocations();
        List<Map<String, Object>> result = list.stream().map(d -> Map.<String, Object>of(
                "activityId", d.getActivityId(),
                "title", d.getTitle() != null ? d.getTitle() : "",
                "urgency", d.getUrgency() != null ? d.getUrgency() : "normal",
                "status", d.getStatus() != null ? d.getStatus() : "created",
                "lat", d.getLat(),
                "lng", d.getLng(),
                "catId", d.getCatId() != null ? d.getCatId() : 0,
                "squarePostId", d.getSquarePostId() != null ? d.getSquarePostId() : 0)).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/guide")
    public ResponseEntity<?> getGuide() {
        List<Map<String, Object>> list = rescueGuideRepository.findAllByOrderBySortOrderAsc().stream()
                .map(g -> Map.<String, Object>of(
                        "id", g.getId(),
                        "title", g.getTitle(),
                        "content", g.getContent(),
                        "sortOrder", g.getSortOrder()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/contacts")
    public ResponseEntity<?> getContacts() {
        List<Map<String, Object>> list = rescueContactRepository.findAllByOrderBySortOrderAsc().stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "name", c.getName(),
                        "phone", c.getPhone(),
                        "description", c.getDescription() != null ? c.getDescription() : "",
                        "sortOrder", c.getSortOrder()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    private Map<String, Object> toMap(RescueActivity a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("description", a.getDescription());
        m.put("catId", a.getCatId());
        m.put("squarePostId", a.getSquarePostId());
        m.put("urgency", a.getUrgency());
        m.put("problemType", a.getProblemType());
        m.put("status", a.getStatus());
        m.put("createdBy", a.getCreatedBy());
        m.put("createdAt", a.getCreatedAt());
        m.put("completedAt", a.getCompletedAt());
        return m;
    }

    @Data
    public static class CreateRescueActivityRequest {
        @NotBlank
        private String title;
        private String description;
        private Long catId;
        private Long squarePostId;
        private String urgency;
        private String problemType;
        private Double latitude;
        private Double longitude;
        private String locationName;
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(rescueService.getStatistics());
    }

    @GetMapping("/articles")
    public ResponseEntity<?> getArticles(@RequestParam(required = false) String category) {
        List<Map<String, Object>> list;
        if (category != null && !category.isEmpty()) {
            list = rescueArticleRepository.findByCategoryOrderBySortOrderAsc(category).stream()
                    .map(a -> Map.<String, Object>of(
                            "id", a.getId(),
                            "title", a.getTitle(),
                            "content", a.getContent(),
                            "category", a.getCategory() != null ? a.getCategory() : "",
                            "sortOrder", a.getSortOrder()))
                    .collect(Collectors.toList());
        } else {
            list = rescueArticleRepository.findAllByOrderBySortOrderAsc().stream()
                    .map(a -> Map.<String, Object>of(
                            "id", a.getId(),
                            "title", a.getTitle(),
                            "content", a.getContent(),
                            "category", a.getCategory() != null ? a.getCategory() : "",
                            "sortOrder", a.getSortOrder()))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/articles/{id}")
    public ResponseEntity<?> getArticle(@PathVariable Long id) {
        return rescueArticleRepository.findById(id)
                .map(a -> ResponseEntity.<Object>ok(Map.of(
                        "id", a.getId(),
                        "title", a.getTitle(),
                        "content", a.getContent(),
                        "category", a.getCategory() != null ? a.getCategory() : "",
                        "sortOrder", a.getSortOrder())))
                .orElse(ResponseEntity.notFound().build());
    }
}
