package com.ourcat.backend.controllers;

import com.ourcat.backend.services.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 猫咪识别接口
 * - POST /detect: 从图片 URL 提取特征并匹配相似猫咪
 * - POST /batch-extract: 批量为现有猫咪提取特征（管理员）
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * 检测图片中的猫咪并匹配相似猫咪
     * 请求体: { "imageUrl": "https://..." }
     * 响应: { "success": true, "message": "...", "candidates": [...] }
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> detect(@RequestBody Map<String, Object> body) {
        String imageUrl = (String) body.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "imageUrl is required"
            ));
        }

        AiService.DetectResult result = aiService.detect(imageUrl);

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success);
        response.put("message", result.message);
        response.put("candidates", result.candidates.stream().map(c -> {
            Map<String, Object> catMap = new HashMap<>();
            catMap.put("catId", c.catId);
            catMap.put("name", c.name);
            catMap.put("color", c.color);
            catMap.put("primaryImageUrl", c.primaryImageUrl);
            catMap.put("confidence", Math.round(c.confidence * 100) / 100.0);
            return catMap;
        }).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * 单独提取特征向量（用于调试或手动处理）
     */
    @PostMapping("/extract-embedding")
    public ResponseEntity<Map<String, Object>> extractEmbedding(@RequestBody Map<String, Object> body) {
        String imageUrl = (String) body.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "imageUrl is required"
            ));
        }

        float[] embedding = aiService.extractEmbedding(imageUrl);
        if (embedding == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to extract embedding"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "embeddingSize", embedding.length
        ));
    }

    /**
     * 为指定猫咪保存特征向量
     */
    @PostMapping("/save-embedding/{catId}")
    @PreAuthorize("hasRole('VOLUNTEER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> saveEmbedding(
            @PathVariable Long catId,
            @RequestBody Map<String, Object> body) {
        String imageUrl = (String) body.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "imageUrl is required"
            ));
        }

        float[] embedding = aiService.extractEmbedding(imageUrl);
        if (embedding == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to extract embedding"
            ));
        }

        aiService.saveEmbedding(catId, embedding);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Embedding saved for cat " + catId
        ));
    }

    /**
     * 批量为所有猫咪提取特征（管理员操作）
     * 注意：此操作耗时较长，会对 Hugging Face API 进行多次调用
     */
    @PostMapping("/batch-extract")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> batchExtract() {
        int count = aiService.batchExtractEmbeddings();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Batch extraction completed",
                "processedCount", count
        ));
    }
}
