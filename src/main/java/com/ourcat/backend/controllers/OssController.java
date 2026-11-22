package com.ourcat.backend.controllers;

import com.ourcat.backend.utils.OssUtil;
import com.ourcat.backend.utils.LocalImageStorage;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssUtil ossUtil;

    @Autowired(required = false)
    private LocalImageStorage localImageStorage;

    /**
     * Get presigned upload URL. Client will PUT file to uploadUrl with header Content-Type: contentType, then use publicUrl in APIs.
     * Supported content types: image/jpeg, image/jpg, image/png, image/webp, image/gif.
     */
    @GetMapping("/upload-url")
    public ResponseEntity<Map<String, String>> getUploadUrl(
            @RequestParam String filename,
            @RequestParam(defaultValue = "image/jpeg") String contentType,
            HttpServletRequest request) {
        if (localImageStorage != null) {
            LocalImageStorage.UploadTicket ticket;
            try {
                ticket = localImageStorage.prepare(contentType);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
            String baseUrl = request.getScheme() + "://" + request.getHeader("Host");
            String path = "/api/oss/local/" + ticket.fileName;
            String uploadUrl = baseUrl + path + "?token="
                    + URLEncoder.encode(ticket.token, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of(
                    "uploadUrl", uploadUrl,
                    "publicUrl", baseUrl + path,
                    "contentType", ticket.contentType
            ));
        }
        OssUtil.PresignResult result = ossUtil.generateUploadPresign(filename, contentType);
        return ResponseEntity.ok(Map.of(
                "uploadUrl", result.uploadUrl != null ? result.uploadUrl : "",
                "publicUrl", result.publicUrl,
                "contentType", result.contentType
        ));
    }
}
