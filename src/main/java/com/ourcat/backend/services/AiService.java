package com.ourcat.backend.services;

import com.ourcat.backend.models.Cat;
import com.ourcat.backend.repositories.CatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final CatRepository catRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String HF_API_URL =
            "https://api-inference.huggingface.co/pipeline/feature-extraction/openai/clip-vit-base-patch32";

    @Value("${ourcat.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${ourcat.ai.hf-token:}")
    private String hfToken;

    @Value("${ourcat.ai.match-threshold:0.7}")
    private double matchThreshold;

    @Value("${ourcat.ai.top-k:5}")
    private int topK;

    /**
     * 从图片 URL 提取特征向量
     */
    public float[] extractEmbedding(String imageUrl) {
        if (!aiEnabled) {
            log.warn("AI service is disabled");
            return null;
        }

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.error("Failed to download image: {}", imageUrl);
                return null;
            }

            String base64 = Base64.getEncoder().encodeToString(imageBytes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (hfToken != null && !hfToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + hfToken);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", base64);
            requestBody.put("options", Map.of("wait_for_model", true));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    HF_API_URL,
                    HttpMethod.POST,
                    entity,
                    List.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<?> body = response.getBody();
                return parseEmbeddingResponse(body);
            }

            log.error("HF API returned non-OK status: {}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("Error extracting embedding from image: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * 解析 Hugging Face API 返回的嵌入向量
     */
    private float[] parseEmbeddingResponse(List<?> body) {
        if (body == null || body.isEmpty()) return null;

        List<Number> numbers = new ArrayList<>();
        flattenList(body, numbers);

        float[] result = new float[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            result[i] = numbers.get(i).floatValue();
        }
        return result;
    }

    private void flattenList(List<?> list, List<Number> result) {
        for (Object item : list) {
            if (item instanceof Number) {
                result.add((Number) item);
            } else if (item instanceof List) {
                flattenList((List<?>) item, result);
            }
        }
    }

    /**
     * 匹配相似猫咪
     */
    public List<CatMatch> matchSimilarCats(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return Collections.emptyList();
        }

        List<Cat> catsWithEmbedding = catRepository.findAllWithEmbedding();
        if (catsWithEmbedding.isEmpty()) {
            return Collections.emptyList();
        }

        return catsWithEmbedding.stream()
                .map(cat -> {
                    float[] catEmbedding = deserializeEmbedding(cat.getAiEmbedding());
                    if (catEmbedding == null) return null;
                    double similarity = cosineSimilarity(embedding, catEmbedding);
                    return new CatMatch(cat, similarity);
                })
                .filter(Objects::nonNull)
                .filter(match -> match.confidence >= matchThreshold)
                .sorted(Comparator.comparingDouble(CatMatch::getConfidence).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 完整检测流程：提取特征 + 匹配
     */
    public DetectResult detect(String imageUrl) {
        DetectResult result = new DetectResult();

        float[] embedding = extractEmbedding(imageUrl);
        if (embedding == null) {
            result.success = false;
            result.message = "特征提取失败";
            return result;
        }

        result.embedding = embedding;
        result.candidates = matchSimilarCats(embedding);
        result.success = true;

        if (result.candidates.isEmpty()) {
            result.message = "未找到匹配的猫咪，建议创建新档案";
        } else {
            result.message = "找到 " + result.candidates.size() + " 个候选匹配";
        }

        return result;
    }

    /**
     * 为猫咪保存特征向量
     */
    public void saveEmbedding(Long catId, float[] embedding) {
        catRepository.findById(catId).ifPresent(cat -> {
            cat.setAiEmbedding(serializeEmbedding(embedding));
            catRepository.save(cat);
        });
    }

    /**
     * 批量为所有猫咪提取特征（用于初始化）
     */
    public int batchExtractEmbeddings() {
        List<Cat> cats = catRepository.findAll();
        int count = 0;

        for (Cat cat : cats) {
            if (cat.getAiEmbedding() != null && cat.getAiEmbedding().length > 0) {
                continue;
            }

            String imageUrl = cat.getPrimaryImageUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                continue;
            }

            try {
                float[] embedding = extractEmbedding(imageUrl);
                if (embedding != null) {
                    cat.setAiEmbedding(serializeEmbedding(embedding));
                    catRepository.save(cat);
                    count++;
                    log.info("Extracted embedding for cat {}: {}", cat.getId(), cat.getName());
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                log.error("Failed to extract embedding for cat {}", cat.getId(), e);
            }
        }

        return count;
    }

    private byte[] downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            try (InputStream is = url.openStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("Failed to download image: {}", imageUrl, e);
            return null;
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            int minLen = Math.min(a.length, b.length);
            a = Arrays.copyOf(a, minLen);
            b = Arrays.copyOf(b, minLen);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private byte[] serializeEmbedding(float[] embedding) {
        ByteBuffer buffer = ByteBuffer.allocate(embedding.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : embedding) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    private float[] deserializeEmbedding(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getFloat();
        }
        return result;
    }

    public static class CatMatch {
        public final Long catId;
        public final String name;
        public final String color;
        public final String primaryImageUrl;
        public final double confidence;

        public CatMatch(Cat cat, double confidence) {
            this.catId = cat.getId();
            this.name = cat.getName();
            this.color = cat.getColor();
            this.primaryImageUrl = cat.getPrimaryImageUrl();
            this.confidence = confidence;
        }

        public double getConfidence() {
            return confidence;
        }
    }

    public static class DetectResult {
        public boolean success;
        public String message;
        public float[] embedding;
        public List<CatMatch> candidates = new ArrayList<>();
    }
}
