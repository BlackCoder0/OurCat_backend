package com.ourcat.backend.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("demo")
public class LocalImageStorage {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long TICKET_TTL_SECONDS = 10 * 60;
    private final Map<String, UploadTicket> tickets = new ConcurrentHashMap<>();

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
        cleanup();
        UploadTicket ticket = new UploadTicket(fileName, token, normalizedType,
                Instant.now().plusSeconds(TICKET_TTL_SECONDS));
        tickets.put(fileName, ticket);
        return ticket;
    }

    public void save(String fileName, String token, String contentType, InputStream input) throws IOException {
        UploadTicket ticket = tickets.get(fileName);
        if (ticket == null || ticket.expiresAt.isBefore(Instant.now()) || !ticket.token.equals(token)) {
            throw new IllegalArgumentException("Upload ticket is invalid or expired");
        }
        if (!ticket.contentType.equals(normalizeContentType(contentType))) {
            throw new IllegalArgumentException("Content-Type does not match the upload ticket");
        }
        if (!tickets.remove(fileName, ticket)) {
            throw new IllegalArgumentException("Upload ticket has already been used");
        }

        byte[] data = readLimited(input);
        validateImage(ticket.contentType, data);
        Files.write(resolve(fileName), data, StandardOpenOption.CREATE_NEW);
    }

    @Scheduled(fixedRate = 60000)
    public void cleanup() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
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

    private void validateImage(String contentType, byte[] data) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid image content");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!contentType.equals("image/" + format)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000
                        || reader.read(0) == null) {
                    throw new IllegalArgumentException("Image type mismatch or more than 20 million pixels");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid or truncated image content", e);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        if ("image/jpeg".equals(normalized) || "image/png".equals(normalized)
                || "image/gif".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Local images support JPEG, PNG and GIF only");
    }

    private String extensionFor(String contentType) {
        if ("image/png".equals(contentType)) return ".png";
        if ("image/gif".equals(contentType)) return ".gif";
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
