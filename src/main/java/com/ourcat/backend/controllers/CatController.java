package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.CatReport;
import com.ourcat.backend.services.CatService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cat")
@RequiredArgsConstructor
public class CatController {

    private final CatService catService;

    @PostMapping("/report")
    public ResponseEntity<?> report(@AuthenticationPrincipal UserPrincipal principal,
                                   @Valid @RequestBody ReportRequest req) {
        if (principal == null) return ResponseEntity.status(401).build();
        CatReport report = catService.report(
                principal.getUser().getId(),
                req.getLat(), req.getLng(),
                req.getImageUrl(), req.getDescription(),
                req.getCatId(), req.getColor(), req.getFeature(), req.getPersonality());
        return ResponseEntity.ok(Map.of(
                "id", report.getId(),
                "lat", report.getLat(),
                "lng", report.getLng(),
                "reportTime", report.getReportTime() != null ? report.getReportTime().toString() : ""
        ));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<List<Map<String, Object>>> heatmap() {
        List<CatService.HeatmapPoint> list = catService.getHeatmap();
        List<Map<String, Object>> result = list.stream().map(p -> Map.<String, Object>of(
                "lat", p.lat, "lng", p.lng, "weight", p.weight
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommend")
    public ResponseEntity<List<Map<String, Object>>> recommend(@RequestParam(defaultValue = "high") String type) {
        boolean high = "high".equalsIgnoreCase(type);
        List<CatService.RecommendItem> list = catService.getRecommend(high);
        List<Map<String, Object>> result = list.stream().map(r -> Map.<String, Object>of(
                "lat", r.lat, "lng", r.lng, "name", r.name, "count", r.count
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-reports")
    public ResponseEntity<List<Map<String, Object>>> myReports(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        List<CatService.LocationDto> list = catService.getMyReports(principal.getUser().getId());
        List<Map<String, Object>> result = list.stream().map(this::dtoToMap).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<?> deleteReport(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(401).build();
        boolean ok = catService.deleteReport(principal.getUser().getId(), id);
        if (ok) return ResponseEntity.ok(Map.of("success", true));
        return ResponseEntity.status(403).body(Map.of("error", "无权删除或记录不存在"));
    }

    @GetMapping("/locations")
    public ResponseEntity<List<Map<String, Object>>> locations() {
        List<CatService.LocationDto> list = catService.getLocations();
        List<Map<String, Object>> result = list.stream().map(this::dtoToMap).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> dtoToMap(CatService.LocationDto d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.id);
        m.put("lat", d.lat);
        m.put("lng", d.lng);
        m.put("imageUrl", d.imageUrl != null ? d.imageUrl : "");
        m.put("description", d.description != null ? d.description : "");
        m.put("reportTime", d.reportTime != null ? d.reportTime : "");
        m.put("userId", d.userId);
        m.put("color", d.color != null ? d.color : "");
        m.put("feature", d.feature != null ? d.feature : "");
        m.put("personality", d.personality != null ? d.personality : "");
        m.put("catId", d.catId);
        m.put("matchConfidence", d.matchConfidence);
        m.put("hasActiveRescue", d.hasActiveRescue);
        m.put("rescueSquarePostId", d.rescueSquarePostId != null ? d.rescueSquarePostId : 0);
        return m;
    }

    // ==================== 猫咪档案管理 API ====================

    /**
     * 获取猫咪档案列表（分页）。支持 keyword、status 筛选；支持 sortBy=nearby 且传入 lat、lng 时按距离就近排序。
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listCats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String sortBy) {
        Map<String, Object> result = catService.listCatsWithSort(page, size, keyword, status, lat, lng, sortBy);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取猫咪详情（含历史上报记录）
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCatDetail(@PathVariable Long id) {
        return catService.getCatDetail(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新猫咪档案（志愿者+）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VOLUNTEER') or hasRole('ADMIN')")
    public ResponseEntity<?> updateCat(
            @PathVariable Long id,
            @RequestBody CatUpdateRequest req) {
        return catService.updateCat(id, req.getName(), req.getColor(),
                        req.getFeature(), req.getPersonality(), req.getStatus(), req.getPrimaryImageUrl())
                .map(cat -> ResponseEntity.ok(catToMap(cat)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 确认上报归属某只猫
     */
    @PostMapping("/report/{reportId}/confirm")
    public ResponseEntity<?> confirmReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId,
            @RequestBody Map<String, Object> body) {
        if (principal == null) return ResponseEntity.status(401).build();
        Long catId = body.get("catId") != null ? ((Number) body.get("catId")).longValue() : null;
        Float confidence = body.get("confidence") != null ? ((Number) body.get("confidence")).floatValue() : null;

        boolean ok = catService.confirmReportCat(reportId, catId, confidence, principal.getUser().getId());
        if (ok) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "确认失败"));
    }

    /**
     * 合并猫咪档案（管理员）
     */
    @PostMapping("/merge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> mergeCats(@RequestBody MergeCatsRequest req) {
        boolean ok = catService.mergeCats(req.getTargetCatId(), req.getSourceCatIds());
        if (ok) {
            return ResponseEntity.ok(Map.of("success", true, "message", "合并成功"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "合并失败"));
    }
    
    // ==================== 用户异议修改 API ====================
    
    /**
     * 获取上报详情（含匹配信息和附近的其他猫咪）
     */
    @GetMapping("/report/{reportId}")
    public ResponseEntity<?> getReportDetail(@PathVariable Long reportId) {
        return catService.getReportDetail(reportId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 用户异议：修改上报的猫咪归属
     * - 可以选择附近的另一只猫
     * - 或标记为新猫（catId 传 null）
     */
    @PostMapping("/report/{reportId}/reassign")
    public ResponseEntity<?> reassignReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId,
            @RequestBody ReassignRequest req) {
        if (principal == null) return ResponseEntity.status(401).build();
        
        boolean ok = catService.reassignReportCat(
                reportId, req.getNewCatId(), req.getIsNewCat(), principal.getUser().getId());
        if (ok) {
            return ResponseEntity.ok(Map.of("success", true, "message", "修改成功"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "修改失败"));
    }
    
    /**
     * 获取附近的猫咪列表（用于异议选择）
     */
    @GetMapping("/nearby-cats")
    public ResponseEntity<?> getNearbyCats(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "15") double radiusKm) {
        List<Map<String, Object>> cats = catService.getNearbyCats(lat, lng, radiusKm);
        return ResponseEntity.ok(cats);
    }

    private Map<String, Object> catToMap(Cat cat) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", cat.getId());
        m.put("name", cat.getName() != null ? cat.getName() : "");
        m.put("color", cat.getColor() != null ? cat.getColor() : "");
        m.put("feature", cat.getFeature() != null ? cat.getFeature() : "");
        m.put("personality", cat.getPersonality() != null ? cat.getPersonality() : "");
        m.put("status", cat.getStatus() != null ? cat.getStatus() : "活跃");
        m.put("primaryImageUrl", cat.getPrimaryImageUrl() != null ? cat.getPrimaryImageUrl() : "");
        m.put("reportCount", cat.getReportCount() != null ? cat.getReportCount() : 0);
        m.put("hasEmbedding", cat.getAiEmbedding() != null && cat.getAiEmbedding().length > 0);
        m.put("createdAt", cat.getCreatedAt() != null ? cat.getCreatedAt().toString() : "");
        return m;
    }

    @Data
    public static class ReportRequest {
        @NotNull private Double lat;
        @NotNull private Double lng;
        private String imageUrl;
        private String description;
        private Long catId;
        private String color;
        private String feature;
        private String personality;
        private Float matchConfidence;
        private Long aiSuggestedCatId;
    }

    @Data
    public static class CatUpdateRequest {
        private String name;
        private String color;
        private String feature;
        private String personality;
        private String status;
        private String primaryImageUrl;
    }

    @Data
    public static class MergeCatsRequest {
        @NotNull private Long targetCatId;
        @NotNull private List<Long> sourceCatIds;
    }
    
    @Data
    public static class ReassignRequest {
        private Long newCatId;
        private Boolean isNewCat;
    }
}
