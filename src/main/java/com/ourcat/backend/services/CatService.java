package com.ourcat.backend.services;

import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.CatReport;
import com.ourcat.backend.models.RescueActivity;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.CatReportRepository;
import com.ourcat.backend.repositories.RescueActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatService {
    private static final List<String> OPEN_RESCUE_STATUSES = List.of("created", "in_progress");

    private final CatRepository catRepository;
    private final CatReportRepository catReportRepository;
    private final RescueActivityRepository rescueActivityRepository;
    private final AiService aiService;

    private static final double MATCH_RADIUS_KM = 15.0;

    @Value("${ourcat.match.combined-threshold:0.68}")
    private double combinedMatchThreshold;
    @Value("${ourcat.match.text-only-threshold:0.68}")
    private double featureMatchThreshold;
    @Value("${ourcat.match.min-image-score-when-both:0.62}")
    private double minImageScoreWhenBoth;

    /** 文本权重（颜色+特征+性格） */
    private static final double TEXT_WEIGHT = 0.5;
    /** 图片 AI 权重 */
    private static final double IMAGE_WEIGHT = 0.5;

    /**
     * 上报猫咪 - 新算法：先保存，后异步匹配
     */
    @Transactional
    public CatReport report(Long userId, double lat, double lng, String imageUrl, String description,
            Long catId, String color, String feature, String personality) {

        CatReport report = CatReport.builder()
                .lat(lat)
                .lng(lng)
                .imageUrl(imageUrl)
                .description(description)
                .catId(null)
                .userId(userId)
                .color(color)
                .feature(feature)
                .personality(personality)
                .confirmed(false)
                .build();
        report = catReportRepository.save(report);

        performAsyncMatching(report.getId());

        return report;
    }

    /**
     * 异步执行猫咪匹配算法
     * 地理（15km）+ 文本特征（颜色/特征/性格）+ 图片 AI 特征向量
     */
    @Async
    public void performAsyncMatching(Long reportId) {
        try {
            Thread.sleep(100);
            doMatching(reportId);
        } catch (Exception e) {
            log.error("异步匹配失败: reportId={}", reportId, e);
        }
    }

    @Transactional
    public void doMatching(Long reportId) {
        Optional<CatReport> opt = catReportRepository.findById(reportId);
        if (opt.isEmpty())
            return;

        CatReport newReport = opt.get();
        if (newReport.getCatId() != null)
            return;

        List<CatReport> nearbyReports = findNearbyReports(
                newReport.getLat(), newReport.getLng(), MATCH_RADIUS_KM, reportId);

        if (nearbyReports.isEmpty()) {
            createNewCatForReport(newReport);
            trySaveEmbeddingForNewCat(reportId);
            return;
        }

        float[] newEmbedding = null;
        if (newReport.getImageUrl() != null && !newReport.getImageUrl().isEmpty()) {
            try {
                newEmbedding = aiService.extractEmbedding(newReport.getImageUrl());
            } catch (Exception e) {
                log.warn("新上报图片特征提取失败，将仅用文本匹配: reportId={}", reportId, e);
            }
        }

        CatReport bestMatch = null;
        double bestScore = 0;

        for (CatReport existing : nearbyReports) {
            if (existing.getCatId() == null)
                continue;

            double textScore = calculateMatchScore(newReport, existing);
            float[] catEmbedding = aiService.getCatEmbedding(existing.getCatId());
            double imageScore = 0.0;
            if (newEmbedding != null && catEmbedding != null) {
                imageScore = aiService.computeSimilarity(newEmbedding, catEmbedding);
            }

            double combinedScore;
            if (newEmbedding != null && catEmbedding != null) {
                combinedScore = TEXT_WEIGHT * textScore + IMAGE_WEIGHT * imageScore;
            } else {
                combinedScore = textScore;
            }

            boolean passThreshold;
            if (newEmbedding != null && catEmbedding != null) {
                passThreshold = combinedScore >= combinedMatchThreshold
                        && imageScore >= minImageScoreWhenBoth;
            } else {
                passThreshold = textScore >= featureMatchThreshold;
            }

            if (combinedScore > bestScore && passThreshold) {
                bestScore = combinedScore;
                bestMatch = existing;
            }
        }

        if (bestMatch != null && bestMatch.getCatId() != null) {
            newReport.setCatId(bestMatch.getCatId());
            newReport.setMatchConfidence((float) bestScore);
            newReport.setAiSuggestedCatId(bestMatch.getCatId());
            catReportRepository.save(newReport);

            Long matchedCatId = bestMatch.getCatId();
            catRepository.findById(matchedCatId).ifPresent(cat -> {
                cat.setReportCount(cat.getReportCount() != null ? cat.getReportCount() + 1 : 1);
                if (cat.getPrimaryImageUrl() == null && newReport.getImageUrl() != null) {
                    cat.setPrimaryImageUrl(newReport.getImageUrl());
                }
                catRepository.save(cat);
            });

            if (newEmbedding != null) {
                trySaveEmbeddingForCatIfMissing(matchedCatId, newEmbedding);
            }

            log.info("匹配成功: reportId={} -> catId={}, score={} (地理+文本+图片)",
                    reportId, bestMatch.getCatId(), bestScore);
        } else {
            createNewCatForReport(newReport);
            trySaveEmbeddingForNewCat(reportId);
        }
    }

    /**
     * 若猫咪尚无 embedding，则用当前上报的向量写入，便于后续匹配使用
     */
    private void trySaveEmbeddingForCatIfMissing(Long catId, float[] embedding) {
        if (catId == null || embedding == null)
            return;
        try {
            if (aiService.getCatEmbedding(catId) == null) {
                aiService.saveEmbedding(catId, embedding);
                log.info("已为猫咪 catId={} 写入首条图片特征，供后续匹配使用", catId);
            }
        } catch (Exception e) {
            log.warn("保存猫咪 embedding 失败: catId={}", catId, e);
        }
    }

    /**
     * 新建猫后，异步为该猫从上报图片提取并保存 embedding
     */
    private void trySaveEmbeddingForNewCat(Long reportId) {
        Optional<CatReport> opt = catReportRepository.findById(reportId);
        if (opt.isEmpty())
            return;
        CatReport report = opt.get();
        if (report.getCatId() == null || report.getImageUrl() == null || report.getImageUrl().isEmpty())
            return;
        try {
            float[] embedding = aiService.extractEmbedding(report.getImageUrl());
            if (embedding != null) {
                aiService.saveEmbedding(report.getCatId(), embedding);
                log.info("新建猫后已写入图片特征: catId={}", report.getCatId());
            }
        } catch (Exception e) {
            log.warn("新建猫后提取/保存 embedding 失败: reportId={}", reportId, e);
        }
    }

    private void createNewCatForReport(CatReport report) {
        Cat newCat = Cat.builder()
                .color(report.getColor())
                .feature(report.getFeature())
                .personality(report.getPersonality())
                .primaryImageUrl(report.getImageUrl())
                .reportCount(1)
                .status("活跃")
                .build();
        newCat = catRepository.save(newCat);

        report.setCatId(newCat.getId());
        report.setConfirmed(false);
        catReportRepository.save(report);

        log.info("创建新猫咪: reportId={} -> newCatId={}", report.getId(), newCat.getId());
    }

    /**
     * 查找指定范围内的上报记录
     */
    public List<CatReport> findNearbyReports(double lat, double lng, double radiusKm, Long excludeId) {
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;
        double minLng = lng - lngDelta;
        double maxLng = lng + lngDelta;

        List<CatReport> candidates = catReportRepository.findAllByOrderByReportTimeDesc().stream()
                .filter(r -> !r.getId().equals(excludeId))
                .filter(r -> r.getLat() >= minLat && r.getLat() <= maxLat)
                .filter(r -> r.getLng() >= minLng && r.getLng() <= maxLng)
                .filter(r -> calculateDistance(lat, lng, r.getLat(), r.getLng()) <= radiusKm)
                .collect(Collectors.toList());

        return candidates;
    }

    /**
     * 计算两点之间的距离（km）- Haversine 公式
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 计算两条上报记录的匹配分数
     * 基于颜色、特征、性格的文字相似度
     */
    private double calculateMatchScore(CatReport r1, CatReport r2) {
        double colorScore = calculateTextSimilarity(r1.getColor(), r2.getColor());
        double featureScore = calculateTextSimilarity(r1.getFeature(), r2.getFeature());
        double personalityScore = calculateTextSimilarity(r1.getPersonality(), r2.getPersonality());

        double colorWeight = 0.5;
        double featureWeight = 0.3;
        double personalityWeight = 0.2;

        return colorScore * colorWeight + featureScore * featureWeight + personalityScore * personalityWeight;
    }

    /**
     * 计算文字相似度（基于关键词重叠）
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null)
            return 0.0;
        if (text1.isEmpty() || text2.isEmpty())
            return 0.0;
        if (text1.equals(text2))
            return 1.0;

        Set<String> keywords1 = extractKeywords(text1);
        Set<String> keywords2 = extractKeywords(text2);

        if (keywords1.isEmpty() || keywords2.isEmpty())
            return 0.0;

        Set<String> intersection = new HashSet<>(keywords1);
        intersection.retainAll(keywords2);

        Set<String> union = new HashSet<>(keywords1);
        union.addAll(keywords2);

        return (double) intersection.size() / union.size();
    }

    /**
     * 从文本中提取关键词
     */
    private Set<String> extractKeywords(String text) {
        if (text == null)
            return Collections.emptySet();
        return Arrays.stream(text.split("[、,，\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    // ==================== 用户异议修改 ====================

    /**
     * 获取上报详情（含匹配信息和附近猫咪）
     * matchedCat：本次上报匹配到的那一只猫（唯一）。nearbyCats：15km 内出现过的一切猫（供参考/异议时选择），非按相似度排序。
     */
    public Optional<Map<String, Object>> getReportDetail(Long reportId) {
        return catReportRepository.findById(reportId).map(report -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", report.getId());
            result.put("lat", report.getLat());
            result.put("lng", report.getLng());
            result.put("imageUrl", report.getImageUrl());
            result.put("description", report.getDescription());
            result.put("color", report.getColor());
            result.put("feature", report.getFeature());
            result.put("personality", report.getPersonality());
            result.put("reportTime", report.getReportTime() != null ? report.getReportTime().toString() : "");
            result.put("catId", report.getCatId());
            result.put("matchConfidence", report.getMatchConfidence());
            result.put("confirmed", report.getConfirmed() != null && report.getConfirmed());

            if (report.getCatId() != null) {
                catRepository.findById(report.getCatId()).ifPresent(cat -> {
                    Map<String, Object> catInfo = new HashMap<>();
                    catInfo.put("id", cat.getId());
                    catInfo.put("name", cat.getName());
                    catInfo.put("color", cat.getColor());
                    catInfo.put("primaryImageUrl", cat.getPrimaryImageUrl());
                    catInfo.put("reportCount", cat.getReportCount());
                    result.put("matchedCat", catInfo);
                });
            }

            List<Map<String, Object>> nearbyCats = getNearbyCats(report.getLat(), report.getLng(), MATCH_RADIUS_KM);
            result.put("nearbyCats", nearbyCats);

            return result;
        });
    }

    /**
     * 获取附近的猫咪列表
     */
    public List<Map<String, Object>> getNearbyCats(double lat, double lng, double radiusKm) {
        List<CatReport> nearbyReports = findNearbyReports(lat, lng, radiusKm, -1L);

        Set<Long> catIds = nearbyReports.stream()
                .map(CatReport::getCatId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return catIds.stream()
                .map(catRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(cat -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", cat.getId());
                    m.put("name", cat.getName() != null ? cat.getName() : "");
                    m.put("color", cat.getColor() != null ? cat.getColor() : "");
                    m.put("feature", cat.getFeature() != null ? cat.getFeature() : "");
                    m.put("primaryImageUrl", cat.getPrimaryImageUrl() != null ? cat.getPrimaryImageUrl() : "");
                    m.put("reportCount", cat.getReportCount() != null ? cat.getReportCount() : 0);
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * 用户异议：重新分配上报的猫咪归属
     */
    @Transactional
    public boolean reassignReportCat(Long reportId, Long newCatId, Boolean isNewCat, Long userId) {
        Optional<CatReport> opt = catReportRepository.findById(reportId);
        if (opt.isEmpty())
            return false;

        CatReport report = opt.get();
        Long oldCatId = report.getCatId();

        if (Boolean.TRUE.equals(isNewCat)) {
            Cat newCat = Cat.builder()
                    .color(report.getColor())
                    .feature(report.getFeature())
                    .personality(report.getPersonality())
                    .primaryImageUrl(report.getImageUrl())
                    .reportCount(1)
                    .status("活跃")
                    .build();
            newCat = catRepository.save(newCat);
            report.setCatId(newCat.getId());
            log.info("用户异议：创建新猫咪 reportId={} -> newCatId={}", reportId, newCat.getId());
        } else if (newCatId != null) {
            Optional<Cat> newCatOpt = catRepository.findById(newCatId);
            if (newCatOpt.isEmpty())
                return false;

            report.setCatId(newCatId);
            Cat newCat = newCatOpt.get();
            newCat.setReportCount(newCat.getReportCount() != null ? newCat.getReportCount() + 1 : 1);
            catRepository.save(newCat);
            log.info("用户异议：重新分配 reportId={} -> catId={}", reportId, newCatId);
        } else {
            return false;
        }

        report.setConfirmed(true);
        catReportRepository.save(report);

        if (oldCatId != null && !oldCatId.equals(report.getCatId())) {
            catRepository.findById(oldCatId).ifPresent(oldCat -> {
                int count = oldCat.getReportCount() != null ? oldCat.getReportCount() - 1 : 0;
                oldCat.setReportCount(Math.max(0, count));
                catRepository.save(oldCat);
            });
        }

        return true;
    }

    public List<LocationDto> getLocations() {
        List<CatReport> reports = catReportRepository.findAllByOrderByReportTimeDesc();
        List<Long> catIds = reports.stream()
                .map(CatReport::getCatId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, RescueActivity> activeRescueByCatId = catIds.isEmpty()
                ? Collections.emptyMap()
                : rescueActivityRepository.findByCatIdInAndStatusInOrderByCreatedAtDesc(catIds, OPEN_RESCUE_STATUSES)
                .stream()
                .filter(activity -> activity.getCatId() != null)
                .collect(Collectors.toMap(
                        RescueActivity::getCatId,
                        activity -> activity,
                        (left, right) -> left,
                        HashMap::new));
        return reports.stream().map(r -> {
            LocationDto dto = new LocationDto(r.getId(), r.getLat(), r.getLng(), r.getImageUrl(), r.getDescription(),
                    r.getReportTime() != null ? r.getReportTime().toString() : "", r.getUserId());
            dto.setCatId(r.getCatId());
            dto.setMatchConfidence(r.getMatchConfidence());
            dto.setColor(r.getColor());
            dto.setFeature(r.getFeature());
            dto.setPersonality(r.getPersonality());
            RescueActivity activity = r.getCatId() != null ? activeRescueByCatId.get(r.getCatId()) : null;
            dto.setHasActiveRescue(activity != null);
            dto.setRescueSquarePostId(activity != null ? activity.getSquarePostId() : null);
            return dto;
        }).collect(Collectors.toList());
    }

    public List<LocationDto> getMyReports(Long userId) {
        List<CatReport> reports = catReportRepository.findByUserIdOrderByReportTimeDesc(userId);
        return reports.stream().map(r -> {
            LocationDto dto = new LocationDto(r.getId(), r.getLat(), r.getLng(), r.getImageUrl(), r.getDescription(),
                    r.getReportTime() != null ? r.getReportTime().toString() : "", r.getUserId());
            dto.setCatId(r.getCatId());
            dto.setMatchConfidence(r.getMatchConfidence());
            dto.setColor(r.getColor());
            dto.setFeature(r.getFeature());
            dto.setPersonality(r.getPersonality());
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

    // ==================== 猫咪档案管理 ====================

    public Page<Cat> listCats(int page, int size, String keyword, String status) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reportCount"));
        if (keyword != null && !keyword.isEmpty()) {
            return catRepository.searchByKeyword(keyword, pageRequest);
        }
        if (status != null && !status.isEmpty()) {
            return catRepository.findByStatus(status, pageRequest);
        }
        return catRepository.findAll(pageRequest);
    }

    private static final double NEARBY_LIST_RADIUS_KM = 100.0;

    /**
     * 分页列表，支持按距离排序（需传入 lat, lng 且 sortBy=nearby）。
     * 返回的 Map 中每项包含 cat 字段及可选的 distanceKm。
     */
    public Map<String, Object> listCatsWithSort(int page, int size, String keyword, String status,
            Double lat, Double lng, String sortBy) {
        if (lat != null && lng != null && "nearby".equalsIgnoreCase(sortBy != null ? sortBy.trim() : "")) {
            List<CatReport> reports = findNearbyReports(lat, lng, NEARBY_LIST_RADIUS_KM, -1L);
            Map<Long, Double> catIdToMinDist = new LinkedHashMap<>();
            for (CatReport r : reports) {
                if (r.getCatId() == null) continue;
                double d = calculateDistance(lat, lng, r.getLat(), r.getLng());
                catIdToMinDist.merge(r.getCatId(), d, Math::min);
            }
            List<Long> sortedIds = catIdToMinDist.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            int total = sortedIds.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            List<Map<String, Object>> content = new ArrayList<>();
            for (int i = from; i < to; i++) {
                Long catId = sortedIds.get(i);
                catRepository.findById(catId).ifPresent(cat -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", cat.getId());
                    m.put("name", cat.getName() != null ? cat.getName() : "");
                    m.put("color", cat.getColor() != null ? cat.getColor() : "");
                    m.put("feature", cat.getFeature() != null ? cat.getFeature() : "");
                    m.put("primaryImageUrl", cat.getPrimaryImageUrl() != null ? cat.getPrimaryImageUrl() : "");
                    m.put("status", cat.getStatus() != null ? cat.getStatus() : "活跃");
                    m.put("reportCount", cat.getReportCount() != null ? cat.getReportCount() : 0);
                    m.put("distanceKm", catIdToMinDist.get(catId));
                    content.add(m);
                });
            }
            int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
            return Map.of(
                    "content", content,
                    "totalElements", (long) total,
                    "totalPages", totalPages,
                    "page", page
            );
        }
        Page<Cat> p = listCats(page, size, keyword, status);
        List<Map<String, Object>> content = p.getContent().stream().map(cat -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", cat.getId());
            m.put("name", cat.getName() != null ? cat.getName() : "");
            m.put("color", cat.getColor() != null ? cat.getColor() : "");
            m.put("feature", cat.getFeature() != null ? cat.getFeature() : "");
            m.put("primaryImageUrl", cat.getPrimaryImageUrl() != null ? cat.getPrimaryImageUrl() : "");
            m.put("status", cat.getStatus() != null ? cat.getStatus() : "活跃");
            m.put("reportCount", cat.getReportCount() != null ? cat.getReportCount() : 0);
            return m;
        }).collect(Collectors.toList());
        return Map.of(
                "content", content,
                "totalElements", p.getTotalElements(),
                "totalPages", p.getTotalPages(),
                "page", page
        );
    }

    public Optional<Map<String, Object>> getCatDetail(Long catId) {
        return catRepository.findById(catId).map(cat -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", cat.getId());
            result.put("name", cat.getName());
            result.put("color", cat.getColor());
            result.put("feature", cat.getFeature());
            result.put("personality", cat.getPersonality());
            result.put("status", cat.getStatus());
            result.put("primaryImageUrl", cat.getPrimaryImageUrl());
            result.put("reportCount", cat.getReportCount());
            result.put("hasEmbedding", cat.getAiEmbedding() != null && cat.getAiEmbedding().length > 0);
            result.put("createdAt", cat.getCreatedAt() != null ? cat.getCreatedAt().toString() : "");
            RescueActivity activeRescue = rescueActivityRepository
                    .findByCatIdAndStatusInOrderByCreatedAtDesc(catId, OPEN_RESCUE_STATUSES)
                    .stream()
                    .findFirst()
                    .orElse(null);
            result.put("hasActiveRescue", activeRescue != null);
            result.put("rescueSquarePostId", activeRescue != null && activeRescue.getSquarePostId() != null
                    ? activeRescue.getSquarePostId()
                    : 0);

            List<CatReport> reports = catReportRepository.findByCatIdOrderByReportTimeDesc(catId);
            List<Map<String, Object>> reportHistory = reports.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", r.getId());
                m.put("lat", r.getLat());
                m.put("lng", r.getLng());
                m.put("imageUrl", r.getImageUrl());
                m.put("description", r.getDescription());
                m.put("reportTime", r.getReportTime() != null ? r.getReportTime().toString() : "");
                m.put("confirmed", r.getConfirmed() != null && r.getConfirmed());
                return m;
            }).collect(Collectors.toList());
            result.put("reportHistory", reportHistory);

            return result;
        });
    }

    @Transactional
    public Optional<Cat> updateCat(Long catId, String name, String color, String feature,
            String personality, String status, String primaryImageUrl) {
        return catRepository.findById(catId).map(cat -> {
            if (name != null)
                cat.setName(name);
            if (color != null)
                cat.setColor(color);
            if (feature != null)
                cat.setFeature(feature);
            if (personality != null)
                cat.setPersonality(personality);
            if (status != null)
                cat.setStatus(status);
            if (primaryImageUrl != null)
                cat.setPrimaryImageUrl(primaryImageUrl);
            return catRepository.save(cat);
        });
    }

    @Transactional
    public boolean confirmReportCat(Long reportId, Long catId, Float confidence, Long userId) {
        Optional<CatReport> opt = catReportRepository.findById(reportId);
        if (opt.isEmpty())
            return false;

        CatReport report = opt.get();
        report.setConfirmed(true);
        report.setMatchConfidence(confidence);

        if (catId != null) {
            report.setCatId(catId);
            catRepository.findById(catId).ifPresent(cat -> {
                cat.setReportCount(cat.getReportCount() != null ? cat.getReportCount() + 1 : 1);
                if (cat.getPrimaryImageUrl() == null && report.getImageUrl() != null) {
                    cat.setPrimaryImageUrl(report.getImageUrl());
                }
                catRepository.save(cat);
            });
        } else {
            Cat newCat = Cat.builder()
                    .primaryImageUrl(report.getImageUrl())
                    .reportCount(1)
                    .build();
            newCat = catRepository.save(newCat);
            report.setCatId(newCat.getId());
        }

        catReportRepository.save(report);
        return true;
    }

    @Transactional
    public boolean mergeCats(Long targetCatId, List<Long> sourceCatIds) {
        Optional<Cat> targetOpt = catRepository.findById(targetCatId);
        if (targetOpt.isEmpty())
            return false;

        Cat target = targetOpt.get();
        int mergedCount = 0;

        for (Long sourceId : sourceCatIds) {
            if (sourceId.equals(targetCatId))
                continue;

            List<CatReport> reports = catReportRepository.findByCatIdOrderByReportTimeDesc(sourceId);
            for (CatReport report : reports) {
                report.setCatId(targetCatId);
                catReportRepository.save(report);
                mergedCount++;
            }

            catRepository.deleteById(sourceId);
        }

        target.setReportCount(target.getReportCount() != null ? target.getReportCount() + mergedCount : mergedCount);
        catRepository.save(target);

        return true;
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
        public Long catId;
        public Float matchConfidence;
        public boolean hasActiveRescue;
        public Long rescueSquarePostId;

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

        public void setCatId(Long catId) {
            this.catId = catId;
        }

        public void setMatchConfidence(Float matchConfidence) {
            this.matchConfidence = matchConfidence;
        }

        public void setHasActiveRescue(boolean hasActiveRescue) {
            this.hasActiveRescue = hasActiveRescue;
        }

        public void setRescueSquarePostId(Long rescueSquarePostId) {
            this.rescueSquarePostId = rescueSquarePostId;
        }
    }
}
