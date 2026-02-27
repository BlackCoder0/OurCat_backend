package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.models.CatReport;
import com.ourcat.backend.services.CatService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/locations")
    public ResponseEntity<List<Map<String, Object>>> locations() {
        List<CatService.LocationDto> list = catService.getLocations();
        List<Map<String, Object>> result = list.stream().map(d -> {
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
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
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
    }
}
