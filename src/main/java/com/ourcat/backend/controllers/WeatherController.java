package com.ourcat.backend.controllers;

import com.ourcat.backend.services.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/warning")
    public ResponseEntity<Map<String, Object>> warning(
            @RequestParam String location) {
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
                .map(warnings -> ResponseEntity.<Map<String, Object>>ok(Map.of("warnings", warnings)))
                .orElse(ResponseEntity.ok(Map.of("warnings", List.of())));
    }
}
