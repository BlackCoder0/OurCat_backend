package com.ourcat.backend.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Component
public class OssUtil {

    @Value("${ourcat.oss.bucket:ourcat}")
    private String bucket;

    @Value("${ourcat.oss.endpoint:}")
    private String endpoint;

    private final OSS ossClient;
    private static final int PRESIGN_EXPIRE_SECONDS = 3600;

    /** Allowed image types for upload; client must send this exact Content-Type when PUT. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    public OssUtil(@org.springframework.beans.factory.annotation.Autowired(required = false) OSS ossClient) {
        this.ossClient = ossClient;
    }

    /**
     * Generate presigned PUT URL for client upload, and the public URL to store in DB.
     * Content-Type is included in the signature so the client must send the same Content-Type when PUT.
     * Allowed types: image/jpeg, image/jpg, image/png, image/webp, image/gif.
     * If OSS is not configured, returns a placeholder publicUrl for dev.
     */
    public PresignResult generateUploadPresign(String filename, String contentType) {
        String key = "uploads/" + UUID.randomUUID() + "-" + sanitizeFilename(filename);
        String normalizedType = normalizeContentType(contentType);
        if (ossClient == null || endpoint == null || endpoint.isEmpty()) {
            return new PresignResult(null, "https://" + bucket + "." + endpoint + "/" + key, normalizedType);
        }
        Date expiration = new Date(System.currentTimeMillis() + PRESIGN_EXPIRE_SECONDS * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key, HttpMethod.PUT);
        request.setExpiration(expiration);
        request.setContentType(normalizedType);
        String presignedUrl = ossClient.generatePresignedUrl(request).toString();
        String publicUrl = "https://" + bucket + "." + endpoint + "/" + key;
        return new PresignResult(presignedUrl, publicUrl, normalizedType);
    }

    /**
     * Normalize and whitelist Content-Type. image/jpg -> image/jpeg; unknown types default to image/jpeg.
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        String trimmed = contentType.trim().toLowerCase();
        if ("image/jpg".equals(trimmed)) {
            return "image/jpeg";
        }
        return ALLOWED_CONTENT_TYPES.contains(trimmed) ? trimmed : "image/jpeg";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "image";
        int i = filename.lastIndexOf('/');
        if (i >= 0) filename = filename.substring(i + 1);
        i = filename.lastIndexOf('\\');
        if (i >= 0) filename = filename.substring(i + 1);
        if (filename.isEmpty()) return "image";
        return filename;
    }

    public static class PresignResult {
        public final String uploadUrl;
        public final String publicUrl;
        /** Content-Type to use when PUT (normalized); client must send this header. */
        public final String contentType;

        public PresignResult(String uploadUrl, String publicUrl) {
            this(uploadUrl, publicUrl, "image/jpeg");
        }

        public PresignResult(String uploadUrl, String publicUrl, String contentType) {
            this.uploadUrl = uploadUrl;
            this.publicUrl = publicUrl;
            this.contentType = contentType != null ? contentType : "image/jpeg";
        }
    }
}
