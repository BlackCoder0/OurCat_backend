package com.ourcat.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * AI 占位接口。后续接入 Python 服务做猫咪检测与相似度识别。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> detect(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "catId", (Object) null,
                "similar", Collections.emptyList()
        ));
    }
}
