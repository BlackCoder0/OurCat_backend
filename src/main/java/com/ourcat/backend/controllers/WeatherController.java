package com.ourcat.backend.controllers;

import com.ourcat.backend.config.UserPrincipal;
import com.ourcat.backend.services.MessageService;
import com.ourcat.backend.services.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final MessageService messageService;

    @GetMapping("/warning")
    public ResponseEntity<Map<String, Object>> warning(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String location,
            @RequestParam(required = false, defaultValue = "false") boolean mock,
            @RequestParam(required = false, defaultValue = "warning") String mockMode) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        String[] parts = location.split(",");
        if (parts.length < 2) {
            return ResponseEntity.badRequest().build();
        }
        double lat, lng;
        try {
            lat = Double.parseDouble(parts[0].trim());
            lng = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }

        if (mock) {
            if (principal.getUser().getRole() == null || principal.getUser().getRole() < 3) {
                return ResponseEntity.status(403).body(Map.of("error", "仅管理员可触发调试"));
            }
            if ("weather".equalsIgnoreCase(mockMode)) {
                return weatherService.getTodayWeatherText(lat, lng)
                        .map(text -> {
                            List<String> one = List.of(text);
                            String content = text;
                            Long messageId = messageService.create(principal.getUser().getId(), "weather_warning",
                                    content,
                                    "weather_warning", null);
                            java.util.Map<String, Object> result = new java.util.HashMap<>();
                            result.put("warnings", one);
                            result.put("messageId", messageId);
                            return ResponseEntity.ok(result);
                        })
                        .orElseGet(() -> {
                            java.util.Map<String, Object> result = new java.util.HashMap<>();
                            result.put("warnings", List.<String>of());
                            result.put("messageId", null);
                            return ResponseEntity.ok(result);
                        });
            }
            List<String> warnings = List.of(
                    "上海中心气象台发布暴雨黄色预警[Ⅳ级/一般]：受江淮气旋影响，预计明天傍晚以前本市大部地区将出现6级阵风7-8级的东南大风，沿江沿海地区7级阵风8-9级，请注意防范低洼积水和出行安全。",
                    "上海中心气象台发布大风蓝色预警[Ⅳ级/一般]：请远离广告牌、临时搭建物和高空坠物区域，注意防风。");
            String content = String.join("\n", warnings);
            Long messageId = messageService.create(principal.getUser().getId(), "weather_warning", content,
                    "weather_warning", null);
            return ResponseEntity.ok(Map.of("warnings", warnings, "messageId", messageId));
        }

        return weatherService.getWarnings(lat, lng)
                .map(warnings -> {
                    Long messageId = null;
                    if (!warnings.isEmpty()) {
                        String content = String.join("\n", warnings);
                        messageId = messageService.create(principal.getUser().getId(), "weather_warning", content,
                                "weather_warning", null);
                    }
                    java.util.Map<String, Object> result = new java.util.HashMap<>();
                    result.put("warnings", warnings);
                    result.put("messageId", messageId);
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    java.util.Map<String, Object> result = new java.util.HashMap<>();
                    result.put("warnings", List.of());
                    result.put("messageId", null);
                    return ResponseEntity.ok(result);
                });
    }

    @GetMapping("/now")
    public ResponseEntity<Map<String, Object>> now(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String location) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String[] parts = location.split(",");
        if (parts.length < 2) {
            return ResponseEntity.badRequest().build();
        }
        double lat;
        double lng;
        try {
            lat = Double.parseDouble(parts[0].trim());
            lng = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        return weatherService.getTodayWeatherText(lat, lng)
                .map(text -> {
                    java.util.Map<String, Object> result = new java.util.HashMap<>();
                    result.put("text", text);
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    java.util.Map<String, Object> result = new java.util.HashMap<>();
                    result.put("text", "暂无实时天气数据");
                    return ResponseEntity.ok(result);
                });
    }
}
