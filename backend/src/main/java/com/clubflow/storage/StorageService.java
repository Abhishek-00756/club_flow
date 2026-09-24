package com.clubflow.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * File storage abstraction. The default implementation writes to local disk; swap in an S3 or
 * Cloudinary implementation by providing another bean and removing LocalStorageService.
 */
public interface StorageService {
    /** Stores the file and returns an opaque key to save in the database. */
    String store(MultipartFile file);

    Resource load(String key);

    void delete(String key);
}
