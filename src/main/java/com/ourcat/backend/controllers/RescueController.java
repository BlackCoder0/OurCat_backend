package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.RescueActivity;
import com.ourcat.backend.models.RescueTask;
import com.ourcat.backend.models.User;
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
import java.util.ArrayList;
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
                "squarePostId", d.getSquarePostId() != null ? d.getSquarePostId() : 0,
                "imageUrl", d.getImageUrl() != null ? d.getImageUrl() : "")).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/guide")
    public ResponseEntity<?> getGuide() {
        return ResponseEntity.ok(buildBuiltinGuide());
    }

    @GetMapping("/contacts")
    public ResponseEntity<?> getContacts() {
        return ResponseEntity.ok(buildBuiltinContacts());
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
        List<Map<String, Object>> all = buildBuiltinArticles();
        if (category == null || category.isBlank()) {
            return ResponseEntity.ok(all);
        }
        List<Map<String, Object>> filtered = all.stream()
                .filter(article -> category.equals(String.valueOf(article.get("category"))))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/articles/{id}")
    public ResponseEntity<?> getArticle(@PathVariable Long id) {
        return buildBuiltinArticles().stream()
                .filter(article -> id.equals(article.get("id")))
                .findFirst()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private List<Map<String, Object>> buildBuiltinGuide() {
        List<Map<String, Object>> guides = new ArrayList<>();
        guides.add(guideItem(
                1L,
                "先判断这是社区猫，还是需要立即介入的个体",
                "先观察是否有耳尖剪标记、项圈、明显外伤或极度消瘦。对健康、警惕的社区猫，先不要立即突然搬离现场；对受伤、被困、呼吸困难或无法站立的猫，尽快联系兽医或救助组织。",
                1));
        guides.add(guideItem(
                2L,
                "健康成猫以 TNR 为优先策略",
                "权威指南都把 Trap-Neuter-Return 作为健康社区猫的核心方案：人道抓捕、绝育、疫苗、耳尖剪标、术后原地放回。这样能降低新增猫增长，也能避免把不适合收容的社区猫反复送入容量紧张的收容体系。",
                2));
        guides.add(guideItem(
                3L,
                "幼猫不要因紧张而立刻抱走",
                "如果发现的是很小的幼猫，而且没有受伤、体温过低或立即危险，通常需要先在远处观察母猫是否会返回。过早移走健康幼猫，可能反而造成人工喂养和成活风险上升。",
                3));
        guides.add(guideItem(
                4L,
                "抓捕与术后监测要按标准流程执行",
                "使用人道诱捕笼，提前预约绝育手术，覆盖笼体减压，并按机构要求禁食。术后要在安静、保暖、封闭的空间监测恢复，确认清醒、呼吸平稳、无异常出血后再放回。",
                4));
        guides.add(guideItem(
                5L,
                "什么情况要优先找兽医或本地救助",
                "如果猫咪出现重度脱水、不吃不动、骨折疑似、眼鼻大量分泌物、被车撞或被线网缠绕，不要等观察，应直接进入医疗或紧急救助通道。",
                5));
        return guides;
    }

    private Map<String, Object> guideItem(Long id, String title, String content, int sortOrder) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("content", content);
        item.put("sortOrder", sortOrder);
        return item;
    }

    private List<Map<String, Object>> buildBuiltinContacts() {
        List<Map<String, Object>> contacts = new ArrayList<>();
        contacts.add(contactItem(
                1L,
                "喵汪的希望小屋",
                "",
                "五邑大学校内流浪猫救助团体，点击前往。",
                1,
                "url",
                "https://www.douyin.com/user/MS4wLjABAAAAKhZ0o8iwfxeekcp-Vue94V4623WyF1w3X4N5PHZ7P6E"));
        return contacts;
    }

    private Map<String, Object> contactItem(Long id, String name, String phone, String description, int sortOrder,
            String actionType, String actionValue) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("phone", phone);
        item.put("description", description);
        item.put("sortOrder", sortOrder);
        item.put("actionType", actionType);
        item.put("actionValue", actionValue);
        return item;
    }

    private List<Map<String, Object>> buildBuiltinArticles() {
        List<Map<String, Object>> articles = new ArrayList<>();
        articles.add(articleItem(
                1L,
                "发现伤病猫时怎么办",
                "1. 不要贸然靠近，避免惊吓。\n2. 观察情况，记录位置与外貌。\n3. 在本平台发起救助活动或到广场发布救助需求。\n4. 联系校园救助组织或下方救助电话。",
                "通用",
                1));
        articles.add(articleItem(
                2L,
                "联系谁",
                "可拨打下方「救助电话」中的校园救助组织电话，或在本平台申请加入组织后参与救助任务。",
                "通用",
                2));
        articles.add(articleItem(
                3L,
                "注意事项",
                "• 注意自身安全，避免被咬伤抓伤。\n• 如需送医，可在地图页查看附近宠物医院。\n• 完成救助后请在任务中填写完成汇报，便于记录。",
                "通用",
                3));
        return articles;
    }

    private Map<String, Object> articleItem(Long id, String title, String content, String category, int sortOrder) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("content", content);
        item.put("category", category);
        item.put("sortOrder", sortOrder);
        return item;
    }
}
