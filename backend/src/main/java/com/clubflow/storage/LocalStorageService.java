package com.clubflow.storage;

import com.clubflow.common.ApiException;
import com.clubflow.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class LocalStorageService implements StorageService {
    private final Path root;

    public LocalStorageService(AppProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create upload directory " + root, ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String key = UUID.randomUUID().toString();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, resolve(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Could not store upload", ex);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "The file could not be saved. Try again.");
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.exists(path)) {
            throw ApiException.notFound("The file is no longer available.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}", key, ex);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.badRequest("Invalid file reference.");
        }
        return path;
    }
}
