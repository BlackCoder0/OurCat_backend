package com.ourcat.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoProfileGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsIsolatedDemoConfigurationAndCreatesDataDirectory() {
        MockEnvironment environment = safeDemoEnvironment();
        Path dataDir = tempDir.resolve("nested/demo-data");
        environment.setProperty("spring.datasource.url", "jdbc:h2:file:" + dataDir.resolve("ourcat")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");

        assertDoesNotThrow(() -> DemoProfileGuard.validate(environment));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(dataDir));
    }

    @Test
    void rejectsCombiningDemoWithAnotherProfile() {
        MockEnvironment environment = safeDemoEnvironment();
        environment.setActiveProfiles("demo", "prod");

        assertThrows(IllegalStateException.class, () -> DemoProfileGuard.validate(environment));
    }

    @Test
    void rejectsNonFileDatabaseAndExternalServices() {
        MockEnvironment environment = safeDemoEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:ourcat");
        assertThrows(IllegalStateException.class, () -> DemoProfileGuard.validate(environment));

        MockEnvironment aiEnvironment = safeDemoEnvironment();
        aiEnvironment.setProperty("ourcat.ai.enabled", "true");
        assertThrows(IllegalStateException.class, () -> DemoProfileGuard.validate(aiEnvironment));

        MockEnvironment ossEnvironment = safeDemoEnvironment();
        ossEnvironment.setProperty("ourcat.oss.access-key-id", "configured");
        assertThrows(IllegalStateException.class, () -> DemoProfileGuard.validate(ossEnvironment));

        MockEnvironment weatherEnvironment = safeDemoEnvironment();
        weatherEnvironment.setProperty("ourcat.qweather.jwt", "configured");
        assertThrows(IllegalStateException.class, () -> DemoProfileGuard.validate(weatherEnvironment));
    }

    private MockEnvironment safeDemoEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        environment.setProperty("spring.datasource.url",
                "jdbc:h2:file:./data/demo/ourcat;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        environment.setProperty("ourcat.jwt.secret", "local-demo-jwt-secret-that-is-long-enough-for-hs256");
        environment.setProperty("ourcat.ai.enabled", "false");
        return environment;
    }
}
