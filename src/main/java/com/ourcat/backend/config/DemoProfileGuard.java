package com.ourcat.backend.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public final class DemoProfileGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROFILE = "demo";
    private static final String DATABASE_PREFIX = "jdbc:h2:file:";
    private static final String DATABASE_SUFFIX = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    static void validate(Environment environment) {
        String[] profiles = ((ConfigurableEnvironment) environment).getActiveProfiles();
        if (Arrays.stream(profiles).noneMatch(PROFILE::equals)) {
            return;
        }
        if (profiles.length != 1) {
            throw new IllegalStateException("The demo profile cannot be combined with other profiles");
        }

        String databaseUrl = environment.getProperty("spring.datasource.url", "");
        if (!databaseUrl.startsWith(DATABASE_PREFIX) || !databaseUrl.endsWith(DATABASE_SUFFIX)) {
            throw new IllegalStateException("The demo profile requires a local H2 file database");
        }

        String databasePath = databaseUrl.substring(DATABASE_PREFIX.length(),
                databaseUrl.length() - DATABASE_SUFFIX.length());
        if (databasePath.isBlank() || databasePath.contains(";")) {
            throw new IllegalStateException("Invalid demo database path");
        }

        requireLocalOnly(environment);
        createDataDirectory(databasePath);
    }

    private static void requireLocalOnly(Environment environment) {
        String secret = environment.getProperty("ourcat.jwt.secret", "");
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Set OURCAT_DEMO_JWT_SECRET to at least 32 bytes");
        }
        if (environment.getProperty("ourcat.ai.enabled", Boolean.class, true)) {
            throw new IllegalStateException("AI must remain disabled in the demo profile");
        }
        for (String property : new String[] {
                "ourcat.oss.endpoint", "ourcat.oss.bucket",
                "ourcat.oss.access-key-id", "ourcat.oss.access-key-secret"
        }) {
            if (!environment.getProperty(property, "").isBlank()) {
                throw new IllegalStateException("OSS credentials are not allowed in the demo profile");
            }
        }

        String username = environment.getProperty("ourcat.demo.admin.username", "");
        String password = environment.getProperty("ourcat.demo.admin.password", "");
        if (username.isBlank() != password.isBlank()) {
            throw new IllegalStateException("Set both demo admin username and password, or neither");
        }
        if (!password.isBlank() && password.length() < 12) {
            throw new IllegalStateException("The demo admin password must contain at least 12 characters");
        }
    }

    private static void createDataDirectory(String databasePath) {
        try {
            Path parent = Paths.get(databasePath).toAbsolutePath().normalize().getParent();
            if (parent == null) {
                throw new IllegalStateException("Invalid demo database path");
            }
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the demo data directory", e);
        }
    }
}
