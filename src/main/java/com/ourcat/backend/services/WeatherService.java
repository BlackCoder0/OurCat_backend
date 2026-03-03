package com.ourcat.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WeatherService {

    @Value("${ourcat.qweather.jwt:}")
    private String jwt;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Call QWeather warning/now. Returns list of warning text or empty.
     */
    @SuppressWarnings("unchecked")
    public Optional<List<String>> getWarnings(double lat, double lng) {
        boolean hasJwt = jwt != null && !jwt.isEmpty();
        if (!hasJwt) {
            return Optional.of(java.util.Collections.emptyList());
        }
        String location = lat + "," + lng;
        try {
            String url = "https://api.qweather.com/v7/warning/now?location=" + location;
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + jwt);
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<Map> respEntity = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            Map<String, Object> resp = respEntity.getBody();
            if (resp == null || !"200".equals(resp.get("code")))
                return Optional.of(java.util.Collections.emptyList());
            Object warning = resp.get("warning");
            if (warning == null)
                return Optional.of(java.util.Collections.emptyList());
            if (warning instanceof List) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) warning;
                return Optional.of(list.stream()
                        .map(m -> m.get("text") != null ? m.get("text").toString() : "")
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList()));
            }
        } catch (Exception ignored) {
        }
        return Optional.of(java.util.Collections.emptyList());
    }
}
