package com.ourcat.backend.controllers;

import com.ourcat.backend.utils.LocalImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@RestController
@Profile("demo")
@RequestMapping("/api/oss/local")
@RequiredArgsConstructor
public class LocalImageController {

    private final LocalImageStorage storage;

    @PutMapping("/{fileName:.+}")
    public ResponseEntity<?> upload(@PathVariable String fileName,
                                    @RequestParam String token,
                                    @RequestHeader(value = "Content-Type", required = false) String contentType,
                                    HttpServletRequest request) {
        try {
            storage.save(fileName, token, contentType, request.getInputStream());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Cannot save image"));
        }
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<byte[]> download(@PathVariable String fileName) {
        try {
            LocalImageStorage.StoredImage image = storage.read(fileName);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .contentType(MediaType.parseMediaType(image.contentType))
                    .body(image.data);
        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
