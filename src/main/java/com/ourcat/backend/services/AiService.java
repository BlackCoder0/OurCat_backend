package com.ourcat.backend.services;

import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.CatReport;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.CatReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
@Slf4j
public class AiService {

    private final CatRepository catRepository;
    private final CatReportRepository catReportRepository;
    private final RestTemplate restTemplate;

    public AiService(CatRepository catRepository, CatReportRepository catReportRepository) {
        this.catRepository = catRepository;
        this.catReportRepository = catReportRepository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5秒连接超时
        factory.setReadTimeout(30000); // 30秒读取超时
        this.restTemplate = new RestTemplate(factory);
    }

    /** 自托管 embedding 服务 URL。若配置则优先使用，不再依赖 HF Inference（DINOv2 等未在 HF 公开部署） */
    @Value("${ourcat.ai.embedding-api-url:}")
    private String embeddingApiUrl;

    /** 使用 Replicate 云端 API 提取 embedding（不占服务器内存，按量付费约 $0.00022/次） */
    @Value("${ourcat.ai.replicate-enabled:false}")
    private boolean replicateEnabled;

    @Value("${ourcat.ai.replicate-api-token:}")
    private String replicateApiToken;

    @Value("${ourcat.ai.replicate-model:krthr/clip-embeddings:1c0371070cb827ec3c7f2f28adcdde54b50dcd239aa6faea0bc98b174ef03fb4}")
    private String replicateModel;

    /** 使用 Replicate 时写入 DB 的模型标识，用于与自托管/DINOv2 区分，仅同模型间可比对 */
    @Value("${ourcat.ai.replicate-model-name:replicate/krthr-clip-embeddings}")
    private String replicateModelName;

    @Value("${ourcat.ai.model-url:https://router.huggingface.co/hf-inference/models/facebook/dinov2-base}")
    private String modelUrl;

    @Value("${ourcat.ai.model-name:facebook/dinov2-base}")
    private String modelName;

    @Value("${ourcat.ai.embedding-dim:768}")
    private int embeddingDim;

    @Value("${ourcat.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${ourcat.ai.hf-token:}")
    private String hfToken;

    @Value("${ourcat.ai.match-threshold:0.7}")
    private double matchThreshold;

    @Value("${ourcat.ai.top-k:5}")
    private int topK;

    /**
     * 提取结果（含失败原因，便于接口返回给调用方排查）
     */
    public static class ExtractEmbeddingResult {
        public final float[] embedding;
        public final String errorMessage;

        private ExtractEmbeddingResult(float[] embedding, String errorMessage) {
            this.embedding = embedding;
            this.errorMessage = errorMessage;
        }

        public static ExtractEmbeddingResult ok(float[] embedding) {
            return new ExtractEmbeddingResult(embedding, null);
        }

        public static ExtractEmbeddingResult fail(String errorMessage) {
            return new ExtractEmbeddingResult(null, errorMessage);
        }
    }

    /**
     * 从图片 URL 提取特征向量（带失败原因，便于接口返回给调用方排查）
     */
    public ExtractEmbeddingResult extractEmbeddingWithDetail(String imageUrl) {
        if (!aiEnabled) {
            return ExtractEmbeddingResult.fail("AI 服务已关闭 (ourcat.ai.enabled=false)");
        }
        boolean useReplicate = replicateEnabled && replicateApiToken != null && !replicateApiToken.isBlank();
        boolean useEmbeddingApi = !useReplicate && embeddingApiUrl != null && !embeddingApiUrl.isBlank();
        if (!useReplicate && !useEmbeddingApi && (hfToken == null || hfToken.isEmpty())) {
            return ExtractEmbeddingResult.fail(
                    "未配置 Replicate (ourcat.ai.replicate-enabled + replicate-api-token)、自托管 (embedding-api-url) 或 HF Token。"
                            + " 服务器内存紧张时可使用 Replicate 按量付费，不占本机内存。");
        }

        if (useReplicate) {
            return extractEmbeddingViaReplicate(imageUrl);
        }

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                return ExtractEmbeddingResult.fail("图片下载失败: " + imageUrl);
            }

            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!useEmbeddingApi) {
                headers.set("Authorization", "Bearer " + hfToken);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", base64);
            if (!useEmbeddingApi) {
                requestBody.put("options", Map.of("wait_for_model", true));
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String requestUrl = useEmbeddingApi ? embeddingApiUrl : modelUrl;
            ResponseEntity<List> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    entity,
                    List.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                String bodyStr = response.getBody() != null ? response.getBody().toString() : "";
                return ExtractEmbeddingResult
                        .fail("Embedding API 返回 " + response.getStatusCode() + " | url=" + requestUrl
                                + (bodyStr.length() > 100 ? "" : " | body=" + bodyStr));
            }
            if (response.getBody() == null) {
                return ExtractEmbeddingResult.fail("API 返回空 body");
            }

            float[] embedding = parseEmbeddingResponse(response.getBody());
            if (embedding == null || embedding.length == 0) {
                return ExtractEmbeddingResult.fail("返回格式无法解析为向量");
            }
            if (embeddingDim > 0 && embedding.length != embeddingDim) {
                return ExtractEmbeddingResult.fail(
                        "向量维度不匹配，期望 " + embeddingDim + "，实际 " + embedding.length);
            }
            return ExtractEmbeddingResult.ok(embedding);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String msg = e.getStatusCode() + ": "
                    + (e.getResponseBodyAsString() != null ? e.getResponseBodyAsString().replaceAll("\\s+", " ").trim()
                            : "");
            if (msg.length() > 200)
                msg = msg.substring(0, 200) + "...";
            String requestUrl = useEmbeddingApi ? embeddingApiUrl : modelUrl;
            return ExtractEmbeddingResult.fail("请求失败 " + msg + " | url=" + requestUrl);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            String requestUrl = useEmbeddingApi ? embeddingApiUrl : modelUrl;
            return ExtractEmbeddingResult.fail("网络/超时: " + (detail != null ? detail : "Unknown")
                    + " | url=" + requestUrl);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.length() > 200)
                msg = msg.substring(0, 200) + "...";
            String requestUrl = useEmbeddingApi ? embeddingApiUrl : modelUrl;
            return ExtractEmbeddingResult.fail("提取异常: " + msg + " | url=" + requestUrl);
        }
    }

    /**
     * 通过 Replicate 云端 API 提取 embedding（不占服务器内存，按量付费）。
     * 直接传图片 URL，由 Replicate 拉取，无需本机下载。
     */
    @SuppressWarnings("unchecked")
    private ExtractEmbeddingResult extractEmbeddingViaReplicate(String imageUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + replicateApiToken);
            headers.set("Prefer", "wait=60");

            Map<String, Object> input = new HashMap<>();
            input.put("image", imageUrl);
            Map<String, Object> body = new HashMap<>();
            body.put("version", replicateModel);
            body.put("input", input);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.replicate.com/v1/predictions",
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED) {
                String bodyStr = response.getBody() != null ? response.getBody().toString() : "";
                return ExtractEmbeddingResult.fail("Replicate 返回 " + response.getStatusCode() + (bodyStr.length() > 150 ? "" : " " + bodyStr));
            }
            Map<String, Object> pred = response.getBody();
            if (pred == null) {
                return ExtractEmbeddingResult.fail("Replicate 返回空 body");
            }
            String status = (String) pred.get("status");
            if (status != null && ("failed".equals(status) || "canceled".equals(status))) {
                String err = pred.get("error") != null ? pred.get("error").toString() : status;
                return ExtractEmbeddingResult.fail("Replicate 执行失败: " + err);
            }
            Object outputObj = pred.get("output");
            if (outputObj == null) {
                return ExtractEmbeddingResult.fail("Replicate 无 output（可能未完成，请稍后重试）");
            }
            if (!(outputObj instanceof Map)) {
                return ExtractEmbeddingResult.fail("Replicate output 格式异常");
            }
            Object embObj = ((Map<?, ?>) outputObj).get("embedding");
            if (embObj == null || !(embObj instanceof List)) {
                return ExtractEmbeddingResult.fail("Replicate output 缺少 embedding 数组");
            }
            float[] embedding = parseEmbeddingResponse((List<?>) embObj);
            if (embedding == null || embedding.length == 0) {
                return ExtractEmbeddingResult.fail("Replicate embedding 解析失败");
            }
            if (embeddingDim > 0 && embedding.length != embeddingDim) {
                return ExtractEmbeddingResult.fail(
                        "向量维度不匹配，期望 " + embeddingDim + "，实际 " + embedding.length);
            }
            return ExtractEmbeddingResult.ok(embedding);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String msg = e.getStatusCode() + ": "
                    + (e.getResponseBodyAsString() != null ? e.getResponseBodyAsString().replaceAll("\\s+", " ").trim() : "");
            if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
            return ExtractEmbeddingResult.fail("Replicate 请求失败 " + msg);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            Throwable cause = e.getCause();
            String detail = cause != null ? cause.getMessage() : e.getMessage();
            return ExtractEmbeddingResult.fail("Replicate 网络/超时: " + (detail != null ? detail : "Unknown"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
            return ExtractEmbeddingResult.fail("Replicate 异常: " + msg);
        }
    }

    private String getEffectiveModelName() {
        return (replicateEnabled && replicateApiToken != null && !replicateApiToken.isBlank())
                ? replicateModelName : modelName;
    }

    public Map<String, Object> getRuntimeConfig() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", aiEnabled);
        m.put("replicateEnabled", replicateEnabled);
        m.put("embeddingApiUrl", embeddingApiUrl != null && !embeddingApiUrl.isBlank() ? embeddingApiUrl : null);
        m.put("modelUrl", modelUrl);
        m.put("modelName", getEffectiveModelName());
        m.put("embeddingDim", embeddingDim);
        m.put("tokenConfigured", hfToken != null && !hfToken.isEmpty());
        return m;
    }

    /**
     * 从图片 URL 提取特征向量（兼容旧调用，失败时仅返回 null）
     */
    public float[] extractEmbedding(String imageUrl) {
        ExtractEmbeddingResult r = extractEmbeddingWithDetail(imageUrl);
        if (r.embedding != null)
            return r.embedding;
        log.warn("extractEmbedding failed: {}", r.errorMessage);
        return null;
    }

    /**
     * 解析 Hugging Face API 返回的嵌入向量
     */
    private float[] parseEmbeddingResponse(List<?> body) {
        if (body == null || body.isEmpty())
            return null;

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
                    if (!isEmbeddingComparable(cat, embedding.length)) {
                        return null;
                    }
                    float[] catEmbedding = deserializeEmbedding(cat.getAiEmbedding());
                    if (catEmbedding == null)
                        return null;
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

        ExtractEmbeddingResult embeddingResult = extractEmbeddingWithDetail(imageUrl);
        if (embeddingResult.embedding == null) {
            result.success = false;
            result.message = embeddingResult.errorMessage != null ? embeddingResult.errorMessage : "特征提取失败";
            return result;
        }
        float[] embedding = embeddingResult.embedding;

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
            cat.setAiEmbeddingModel(getEffectiveModelName());
            cat.setAiEmbeddingDim(embedding != null ? embedding.length : null);
            cat.setAiEmbeddingUpdatedAt(java.time.Instant.now());
            catRepository.save(cat);
        });
    }

    /**
     * 获取猫咪已存储的特征向量（用于匹配时与上报图片的向量比较）
     */
    public float[] getCatEmbedding(Long catId) {
        if (catId == null)
            return null;
        return catRepository.findById(catId)
                .map(Cat::getAiEmbedding)
                .filter(bytes -> bytes != null && bytes.length > 0)
                .map(this::deserializeEmbedding)
                .orElse(null);
    }

    /**
     * 计算两个特征向量的余弦相似度 [0, 1]，供匹配流程组合文本+图片分数
     */
    public double computeSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0)
            return 0.0;
        double sim = cosineSimilarity(a, b);
        return Math.max(0.0, Math.min(1.0, (sim + 1) / 2));
    }

    /**
     * 批量为所有猫咪提取特征（用于初始化或回填已有数据）
     * 优先使用猫咪的 primary_image_url；若为空则用该猫第一条上报的图片
     * 
     * @param limit 每次最多处理多少只猫（避免超时）
     */
    public int batchExtractEmbeddings(int limit) {
        List<Cat> cats = catRepository.findAll();
        int count = 0;
        int processed = 0;

        for (Cat cat : cats) {
            if (processed >= limit)
                break;

            if (cat.getAiEmbedding() != null && cat.getAiEmbedding().length > 0) {
                continue;
            }

            processed++;

            String imageUrl = cat.getPrimaryImageUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                List<CatReport> reports = catReportRepository.findByCatIdOrderByReportTimeDesc(cat.getId());
                if (!reports.isEmpty() && reports.get(0).getImageUrl() != null
                        && !reports.get(0).getImageUrl().isEmpty()) {
                    imageUrl = reports.get(0).getImageUrl();
                    log.debug("猫 catId={} 无主图，使用首条上报图片回填 embedding", cat.getId());
                }
            }
            if (imageUrl == null || imageUrl.isEmpty()) {
                continue;
            }

            try {
                float[] embedding = extractEmbedding(imageUrl);
                if (embedding != null) {
                    cat.setAiEmbedding(serializeEmbedding(embedding));
                    cat.setAiEmbeddingModel(getEffectiveModelName());
                    cat.setAiEmbeddingDim(embedding.length);
                    cat.setAiEmbeddingUpdatedAt(java.time.Instant.now());
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
            java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            try (InputStream is = conn.getInputStream();
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

        if (normA == 0 || normB == 0)
            return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private boolean isEmbeddingComparable(Cat cat, int queryDim) {
        byte[] bytes = cat.getAiEmbedding();
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        Integer dim = cat.getAiEmbeddingDim();
        if (dim == null || dim <= 0) {
            dim = bytes.length / 4;
        }
        if (dim != queryDim) {
            return false;
        }
        String model = cat.getAiEmbeddingModel();
        if (model == null || model.isEmpty()) {
            return false;
        }
        return getEffectiveModelName() != null && getEffectiveModelName().equalsIgnoreCase(model);
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
        if (bytes == null || bytes.length == 0)
            return null;
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
