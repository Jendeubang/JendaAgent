package com.jd.genie.service.agent;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Minimal immutable MultipartFile used when an upstream provider image is archived. */
final class InMemoryAgentImageMultipartFile implements MultipartFile {
    private final String originalFileName;
    private final String contentType;
    private final byte[] content;

    InMemoryAgentImageMultipartFile(String originalFileName, String contentType, byte[] content) {
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.content = Arrays.copyOf(content, content.length);
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return originalFileName;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(content, content.length);
    }

    @Override
    public InputStream getInputStream() {
        return new java.io.ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.copy(getInputStream(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
