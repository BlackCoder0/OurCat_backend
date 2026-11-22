package com.ourcat.backend.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalImageStorageTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsAnImageWithOneTimeTicket() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(tempDir.toString());
        LocalImageStorage.UploadTicket ticket = storage.prepare("image/png");

        storage.save(ticket.fileName, ticket.token, "image/png", new ByteArrayInputStream(PNG));
        LocalImageStorage.StoredImage image = storage.read(ticket.fileName);

        assertEquals("image/png", image.contentType);
        assertArrayEquals(PNG, image.data);
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(ticket.fileName, ticket.token, "image/png", new ByteArrayInputStream(PNG)));
    }

    @Test
    void rejectsContentThatDoesNotMatchTheDeclaredImageType() {
        LocalImageStorage storage = new LocalImageStorage(tempDir.toString());
        LocalImageStorage.UploadTicket ticket = storage.prepare("image/png");

        assertThrows(IllegalArgumentException.class,
                () -> storage.save(ticket.fileName, ticket.token, "image/png",
                        new ByteArrayInputStream("not-an-image".getBytes())));
    }
}
