package com.ourcat.backend.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("demo")
public class LocalImageStorage {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long TICKET_TTL_SECONDS = 10 * 60;
    private static final Map<String, UploadTicket> TICKETS = new ConcurrentHashMap<>();

    private final Path imageDirectory;

    public LocalImageStorage(@Value("${ourcat.demo.data-dir:./data/demo}") String dataDirectory) {
        try {
            imageDirectory = Paths.get(dataDirectory).toAbsolutePath().normalize().resolve("images");
            Files.createDirectories(imageDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the local image directory", e);
        }
    }

    public UploadTicket prepare(String contentType) {
        String normalizedType = normalizeContentType(contentType);
        String fileName = UUID.randomUUID() + extensionFor(normalizedType);
        String token = UUID.randomUUID().toString();
        TICKETS.put(fileName, new UploadTicket(fileName, token, normalizedType,
                Instant.now().plusSeconds(TICKET_TTL_SECONDS)));
        return new UploadTicket(fileName, token, normalizedType, null);
    }

    public void save(String fileName, String token, String contentType, InputStream input) throws IOException {
        UploadTicket ticket = TICKETS.remove(fileName);
        if (ticket == null || ticket.expiresAt.isBefore(Instant.now()) || !ticket.token.equals(token)) {
            throw new IllegalArgumentException("Upload ticket is invalid or expired");
        }
        if (!ticket.contentType.equals(normalizeContentType(contentType))) {
            throw new IllegalArgumentException("Content-Type does not match the upload ticket");
        }

        byte[] data = readLimited(input);
        if (!matchesImageType(ticket.contentType, data)) {
            throw new IllegalArgumentException("Uploaded content is not a supported image");
        }
        Files.write(resolve(fileName), data, StandardOpenOption.CREATE_NEW);
    }

    public StoredImage read(String fileName) throws IOException {
        Path path = resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Image not found");
        }
        return new StoredImage(Files.readAllBytes(path), contentTypeFor(fileName));
    }

    private Path resolve(String fileName) {
        if (fileName == null || !fileName.matches("^[0-9a-fA-F-]{36}\\.(jpg|png|gif|webp)$")) {
            throw new IllegalArgumentException("Invalid image name");
        }
        Path path = imageDirectory.resolve(fileName).normalize();
        if (!path.getParent().equals(imageDirectory)) {
            throw new IllegalArgumentException("Invalid image path");
        }
        return path;
    }

    private byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Image exceeds the 10 MB limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean matchesImageType(String contentType, byte[] data) {
        if ("image/jpeg".equals(contentType)) {
            return data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8
                    && (data[2] & 0xff) == 0xff;
        }
        if ("image/png".equals(contentType)) {
            return data.length >= 8 && Arrays.equals(Arrays.copyOf(data, 8),
                    new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
        }
        if ("image/gif".equals(contentType)) {
            return data.length >= 6 && ("GIF87a".equals(new String(data, 0, 6))
                    || "GIF89a".equals(new String(data, 0, 6)));
        }
        return data.length >= 12 && "RIFF".equals(new String(data, 0, 4))
                && "WEBP".equals(new String(data, 8, 4));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        String normalized = contentType.trim().toLowerCase();
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        if ("image/jpeg".equals(normalized) || "image/png".equals(normalized)
                || "image/gif".equals(normalized) || "image/webp".equals(normalized)) {
            return normalized;
        }
        return "image/jpeg";
    }

    private String extensionFor(String contentType) {
        if ("image/png".equals(contentType)) return ".png";
        if ("image/gif".equals(contentType)) return ".gif";
        if ("image/webp".equals(contentType)) return ".webp";
        return ".jpg";
    }

    private String contentTypeFor(String fileName) {
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    public static class UploadTicket {
        public final String fileName;
        public final String token;
        public final String contentType;
        private final Instant expiresAt;

        private UploadTicket(String fileName, String token, String contentType, Instant expiresAt) {
            this.fileName = fileName;
            this.token = token;
            this.contentType = contentType;
            this.expiresAt = expiresAt;
        }
    }

    public static class StoredImage {
        public final byte[] data;
        public final String contentType;

        public StoredImage(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }
    }
}
