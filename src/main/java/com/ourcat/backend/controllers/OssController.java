package com.ourcat.backend.controllers;

import com.ourcat.backend.utils.OssUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssUtil ossUtil;

    /**
     * Get presigned upload URL. Client will PUT file to uploadUrl with header Content-Type: contentType, then use publicUrl in APIs.
     * Supported content types: image/jpeg, image/jpg, image/png, image/webp, image/gif.
     */
    @GetMapping("/upload-url")
    public ResponseEntity<Map<String, String>> getUploadUrl(
            @RequestParam String filename,
            @RequestParam(defaultValue = "image/jpeg") String contentType) {
        OssUtil.PresignResult result = ossUtil.generateUploadPresign(filename, contentType);
        return ResponseEntity.ok(Map.of(
                "uploadUrl", result.uploadUrl != null ? result.uploadUrl : "",
                "publicUrl", result.publicUrl,
                "contentType", result.contentType
        ));
    }
}
