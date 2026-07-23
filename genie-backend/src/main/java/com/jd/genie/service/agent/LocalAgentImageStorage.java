package com.jd.genie.service.agent;

import com.jd.genie.model.agent.StoredAgentImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Development storage adapter. Replace with a COS implementation by providing another AgentImageStorage bean.
 */
@Service
@ConditionalOnProperty(name = "agent.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalAgentImageStorage implements AgentImageStorage {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private final Path rootDirectory;

    public LocalAgentImageStorage(@Value("${agent.storage.local-directory:./data/uploads/agent}") String localDirectory) {
        this.rootDirectory = Path.of(localDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to initialize local agent upload directory", error);
        }
    }

    @Override
    public StoredAgentImage store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image file must not exceed 10 MB");
        }
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFileName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only jpg, jpeg, png, webp and gif images are supported");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Uploaded content must be an image");
        }

        String assetId = UUID.randomUUID().toString();
        String storedFileName = assetId + "." + extension.toLowerCase(Locale.ROOT);
        Path target = rootDirectory.resolve(storedFileName).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Invalid image file name");
        }
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save uploaded image", error);
        }
        return new StoredAgentImage(assetId, originalFileName, storedFileName, file.getContentType(), file.getSize());
    }
}
