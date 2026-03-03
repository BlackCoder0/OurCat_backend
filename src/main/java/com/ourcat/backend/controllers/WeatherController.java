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
            @RequestParam String location) {
        if (principal == null) return ResponseEntity.status(401).build();
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
        return weatherService.getWarnings(lat, lng)
                .map(warnings -> {
                    Long messageId = null;
                    if (!warnings.isEmpty()) {
                        String content = String.join("\n", warnings);
                        messageId = messageService.create(principal.getUser().getId(), "weather_warning", content, "weather_warning", null);
                    }
                    return ResponseEntity.<Map<String, Object>>ok(Map.of("warnings", warnings, "messageId", messageId));
                })
                .orElse(ResponseEntity.ok(Map.of("warnings", List.of(), "messageId", null)));
    }
}
