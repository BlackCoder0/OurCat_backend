package com.ourcat.backend.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class OssUtil {

    @Value("${ourcat.oss.bucket:ourcat}")
    private String bucket;

    @Value("${ourcat.oss.endpoint:}")
    private String endpoint;

    private final OSS ossClient;
    private static final int PRESIGN_EXPIRE_SECONDS = 3600;

    public OssUtil(@org.springframework.beans.factory.annotation.Autowired(required = false) OSS ossClient) {
        this.ossClient = ossClient;
    }

    /**
     * Generate presigned PUT URL for client upload, and the public URL to store in DB.
     * If OSS is not configured, returns a placeholder publicUrl for dev.
     */
    public PresignResult generateUploadPresign(String filename, String contentType) {
        if (ossClient == null || endpoint == null || endpoint.isEmpty()) {
            String key = "uploads/" + UUID.randomUUID() + "-" + sanitizeFilename(filename);
            return new PresignResult(null, "https://" + bucket + "." + endpoint + "/" + key);
        }
        String key = "uploads/" + UUID.randomUUID() + "-" + sanitizeFilename(filename);
        Date expiration = new Date(System.currentTimeMillis() + PRESIGN_EXPIRE_SECONDS * 1000);
        String presignedUrl = ossClient.generatePresignedUrl(bucket, key, expiration, HttpMethod.PUT).toString();
        String publicUrl = "https://" + bucket + "." + endpoint + "/" + key;
        return new PresignResult(presignedUrl, publicUrl);
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

        public PresignResult(String uploadUrl, String publicUrl) {
            this.uploadUrl = uploadUrl;
            this.publicUrl = publicUrl;
        }
    }
}
