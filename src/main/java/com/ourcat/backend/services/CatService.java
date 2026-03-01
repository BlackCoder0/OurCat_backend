package com.ourcat.backend.services;

import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.CatReport;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.CatReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatService {

    private final CatRepository catRepository;
    private final CatReportRepository catReportRepository;

    @Transactional
    public CatReport report(Long userId, double lat, double lng, String imageUrl, String description,
            Long catId, String color, String feature, String personality) {
        Long resolvedCatId = catId;
        if (resolvedCatId == null && (color != null || feature != null || personality != null)) {
            Cat cat = Cat.builder()
                    .color(color)
                    .feature(feature)
                    .personality(personality)
                    .build();
            cat = catRepository.save(cat);
            resolvedCatId = cat.getId();
        }
        CatReport report = CatReport.builder()
                .lat(lat)
                .lng(lng)
                .imageUrl(imageUrl)
                .description(description)
                .catId(resolvedCatId)
                .userId(userId)
                .build();
        return catReportRepository.save(report);
    }

    public List<LocationDto> getLocations() {
        List<CatReport> reports = catReportRepository.findAllByOrderByReportTimeDesc();
        return reports.stream().map(r -> {
            LocationDto dto = new LocationDto(r.getId(), r.getLat(), r.getLng(), r.getImageUrl(), r.getDescription(),
                    r.getReportTime() != null ? r.getReportTime().toString() : "", r.getUserId());
            if (r.getCatId() != null) {
                catRepository.findById(r.getCatId()).ifPresent(c -> {
                    dto.setColor(c.getColor());
                    dto.setFeature(c.getFeature());
                    dto.setPersonality(c.getPersonality());
                });
            }
            return dto;
        }).collect(Collectors.toList());
    }

    public List<LocationDto> getMyReports(Long userId) {
        List<CatReport> reports = catReportRepository.findByUserIdOrderByReportTimeDesc(userId);
        return reports.stream().map(r -> {
            LocationDto dto = new LocationDto(r.getId(), r.getLat(), r.getLng(), r.getImageUrl(), r.getDescription(),
                    r.getReportTime() != null ? r.getReportTime().toString() : "", r.getUserId());
            if (r.getCatId() != null) {
                catRepository.findById(r.getCatId()).ifPresent(c -> {
                    dto.setColor(c.getColor());
                    dto.setFeature(c.getFeature());
                    dto.setPersonality(c.getPersonality());
                });
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public boolean deleteReport(Long userId, Long reportId) {
        Optional<CatReport> opt = catReportRepository.findById(reportId);
        if (opt.isEmpty())
            return false;
        CatReport report = opt.get();
        if (!report.getUserId().equals(userId))
            return false;
        catReportRepository.delete(report);
        return true;
    }

    /**
     * Heatmap: last 30 days, 500m grid (simplified: round lat/lng to 2 decimals as
     * grid key), weight = count.
     */
    public List<HeatmapPoint> getHeatmap() {
        Instant since = Instant.now().minusSeconds(30L * 24 * 3600);
        List<CatReport> reports = catReportRepository.findAllByOrderByReportTimeDesc().stream()
                .filter(r -> r.getReportTime() != null && r.getReportTime().isAfter(since))
                .collect(Collectors.toList());
        Map<String, Double> grid = new HashMap<>();
        for (CatReport r : reports) {
            String key = String.format("%.2f,%.2f", r.getLat(), r.getLng());
            grid.merge(key, 1.0, Double::sum);
        }
        return grid.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split(",");
                    return new HeatmapPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                            e.getValue().intValue());
                })
                .collect(Collectors.toList());
    }

    /**
     * Recommend: high = top5 by report count in grid, low = bottom 5 (or least
     * reported).
     */
    public List<RecommendItem> getRecommend(boolean high) {
        List<HeatmapPoint> points = getHeatmap();
        List<HeatmapPoint> sorted = points.stream().sorted(Comparator.comparingInt(HeatmapPoint::getWeight).reversed())
                .collect(Collectors.toList());
        if (sorted.isEmpty())
            return Collections.emptyList();
        int take = Math.min(5, sorted.size());
        if (high) {
            return sorted.subList(0, take).stream()
                    .map(p -> new RecommendItem(p.getLat(), p.getLng(), "区域", p.getWeight()))
                    .collect(Collectors.toList());
        } else {
            List<HeatmapPoint> low = sorted.subList(Math.max(0, sorted.size() - take), sorted.size());
            return low.stream()
                    .map(p -> new RecommendItem(p.getLat(), p.getLng(), "区域", p.getWeight()))
                    .collect(Collectors.toList());
        }
    }

    public static class HeatmapPoint {
        public final double lat;
        public final double lng;
        public final int weight;

        public HeatmapPoint(double lat, double lng, int weight) {
            this.lat = lat;
            this.lng = lng;
            this.weight = weight;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }

        public int getWeight() {
            return weight;
        }
    }

    public static class RecommendItem {
        public final double lat;
        public final double lng;
        public final String name;
        public final int count;

        public RecommendItem(double lat, double lng, String name, int count) {
            this.lat = lat;
            this.lng = lng;
            this.name = name;
            this.count = count;
        }
    }

    public static class LocationDto {
        public long id;
        public double lat;
        public double lng;
        public String imageUrl;
        public String description;
        public String reportTime;
        public long userId;
        public String color;
        public String feature;
        public String personality;

        public LocationDto(long id, double lat, double lng, String imageUrl, String description, String reportTime,
                long userId) {
            this.id = id;
            this.lat = lat;
            this.lng = lng;
            this.imageUrl = imageUrl;
            this.description = description;
            this.reportTime = reportTime;
            this.userId = userId;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public void setFeature(String feature) {
            this.feature = feature;
        }

        public void setPersonality(String personality) {
            this.personality = personality;
        }
    }
}
