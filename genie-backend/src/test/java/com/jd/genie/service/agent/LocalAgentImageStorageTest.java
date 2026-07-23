package com.jd.genie.service.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAgentImageStorageTest {
    @Test
    void storesValidatedImageInConfiguredDirectory(@TempDir Path uploadDirectory) throws Exception {
        LocalAgentImageStorage storage = new LocalAgentImageStorage(uploadDirectory.toString());
        MockMultipartFile file = new MockMultipartFile("file", "reference.png", "image/png", new byte[]{1, 2, 3});

        var stored = storage.store(file);

        assertEquals("reference.png", stored.originalFileName());
        assertTrue(stored.storedFileName().endsWith(".png"));
        assertTrue(Files.exists(uploadDirectory.resolve(stored.storedFileName())));
    }
}
