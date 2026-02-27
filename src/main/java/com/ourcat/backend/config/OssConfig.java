package com.ourcat.backend.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssConfig {

    @Value("${ourcat.oss.endpoint:}")
    private String endpoint;

    @Value("${ourcat.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${ourcat.oss.access-key-secret:}")
    private String accessKeySecret;

    @Bean
    public OSS ossClient() {
        if (endpoint == null || endpoint.isEmpty() || accessKeyId == null || accessKeyId.isEmpty()) {
            return null;
        }
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
