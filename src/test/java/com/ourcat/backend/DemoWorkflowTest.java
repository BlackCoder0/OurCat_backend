package com.ourcat.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourcat.backend.config.DemoProfileGuard;
import com.ourcat.backend.utils.CaptchaStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@ContextConfiguration(initializers = DemoProfileGuard.class)
class DemoWorkflowTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired CaptchaStore captchas;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String dir = "./target/demo-workflow-" + UUID.randomUUID();
        registry.add("ourcat.demo.data-dir", () -> dir);
        registry.add("ourcat.jwt.secret", () -> "test-only-jwt-secret-at-least-32-bytes-long");
        registry.add("ourcat.demo.admin.username", () -> "workflow-admin");
        registry.add("ourcat.demo.admin.password", () -> "workflow-admin-password");
    }

    @Test
    void registrationImagesOrganizationAndRescuePermissionsWorkTogether() throws Exception {
        String admin = request(post("/api/auth/login"), null, Map.of("username", "workflow-admin",
                "password", "workflow-admin-password", "captcha", "TEST", "captchaKey", captchas.put("TEST")), 200)
                .get("token").asText();
        JsonNode registered = request(post("/api/auth/register"), null, Map.of("username", "volunteer",
                "password", "volunteer-password", "captcha", "TEST", "captchaKey", captchas.put("TEST")), 200);
        String volunteer = registered.get("token").asText();
        long userId = registered.get("user").get("id").asLong();
        String outsider = request(post("/api/auth/register"), null, Map.of("username", "outsider",
                "password", "outsider-password", "captcha", "TEST", "captchaKey", captchas.put("TEST")), 200)
                .get("token").asText();
        request(get("/api/user/list"), outsider, null, 403);
        request(get("/api/oss/upload-url").param("filename", "test.png"), null, null, 403);

        JsonNode upload = request(get("/api/oss/upload-url").param("filename", "test.png")
                .param("contentType", "image/png"), volunteer, null, 200);
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        String uploadPath = upload.get("uploadUrl").asText().replace("http://localhost", "");
        request(put(uploadPath).contentType("image/png").content(png), null, null, 204);
        request(put(uploadPath).contentType("image/png").content(png), null, null, 400);
        String image = upload.get("publicUrl").asText();
        assertArrayEquals(png, http.perform(get(image.replace("http://localhost", "")))
                .andReturn().getResponse().getContentAsByteArray());
        request(put("/api/user/profile"), volunteer, Map.of("nickname", "本地志愿者", "avatarUrl", image), 200);
        request(post("/api/forum/posts"), volunteer, Map.of("title", "带图测试", "content", "本地图片", "images", java.util.List.of(image)), 200);
        request(post("/api/cat/report"), volunteer, Map.of("lat", 31.23, "lng", 121.47, "imageUrl", image,
                "description", "本地上报", "catId", 2), 200);
        request(post("/api/square/posts"), volunteer, Map.of("text", "本地图片广播", "type", "inquiry",
                "images", java.util.List.of(image)), 200);

        request(post("/api/org/join"), volunteer, null, 200);
        request(get("/api/org/join-requests"), outsider, null, 403);
        long joinId = request(get("/api/org/join-requests"), admin, null, 200).get(0).get("id").asLong();
        request(patch("/api/org/join-requests/" + joinId), admin, Map.of("approve", true), 200);
        assertEquals(2, request(get("/api/user/profile"), volunteer, null, 200).get("role").asInt());

        request(patch("/api/rescue/activities/1"), outsider, Map.of("status", "completed"), 404);
        request(post("/api/rescue/activities/1/tasks"), outsider, Map.of("assigneeUserId", userId), 400);
        request(post("/api/rescue/activities/1/tasks/claim"), outsider, null, 400);
        long task = request(post("/api/rescue/activities/1/tasks"), admin, Map.of("assigneeUserId", userId), 200).get("id").asLong();
        request(post("/api/rescue/tasks/" + task + "/logs"), outsider, Map.of("content", "越权"), 403);
        request(post("/api/rescue/tasks/" + task + "/logs"), volunteer, Map.of("content", "已到现场", "images", "[\"" + image + "\"]"), 200);
        request(patch("/api/rescue/tasks/" + task), volunteer, Map.of("status", "done", "completionNote", "已完成"), 200);
        request(patch("/api/rescue/activities/1"), volunteer, Map.of("status", "in_progress"), 200);
        request(patch("/api/rescue/activities/1"), admin, Map.of("status", "completed"), 200);
        long activity = request(post("/api/rescue/activities"), volunteer, Map.of("title", "自领任务", "catId", 2, "problemType", "其他"), 200).get("id").asLong();
        request(post("/api/rescue/activities/" + activity + "/tasks/claim"), volunteer, null, 200);
        long squareId = request(get("/api/rescue/activities/" + activity), volunteer, null, 200).get("squarePostId").asLong();
        request(post("/api/square/posts/" + squareId + "/markSolved"), outsider, null, 403);

        JsonNode rescuePost = request(post("/api/square/posts"), outsider,
                Map.of("text", "成员协助完结", "type", "rescue", "location", "31.23,121.47"), 200);
        long linkedActivity = rescuePost.get("rescueActivityId").asLong();
        request(post("/api/rescue/activities/" + linkedActivity + "/tasks"), admin, Map.of("assigneeUserId", userId), 200);
        request(post("/api/square/posts/" + rescuePost.get("id").asLong() + "/markSolved"), volunteer, null, 200);
        assertEquals("completed", request(get("/api/rescue/activities/" + linkedActivity), volunteer, null, 200).get("status").asText());
        assertEquals("done", request(get("/api/rescue/activities/" + linkedActivity + "/tasks"), volunteer, null, 200).get(0).get("status").asText());
    }

    private JsonNode request(MockHttpServletRequestBuilder request, String token, Object body, int status) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
        var response = http.perform(request).andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
        return response.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }
}
