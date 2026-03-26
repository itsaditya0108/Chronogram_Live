package com.company.image_service.service.storage;

import com.company.image_service.dto.StoredImageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class LocalStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);

    @Autowired
    private com.company.image_service.service.EncryptionService encryptionService;

    @Value("${image.storage.base-path:./data/image-service}")
    private String basePath;

    @Override
    public StoredImageResult store(InputStream stream, String originalFilename, Long userId, String type, BufferedImage source) {
        try {
            return com.company.image_service.util.FileStorageUtil.storeWithThumbnail(
                    stream, originalFilename, userId, basePath, source, type, encryptionService
            );
        } catch (Exception e) {
            logger.error("Failed to store file locally: {}", originalFilename, e);
            throw new RuntimeException("Local storage failed", e);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            // Local storage paths in DB are relative to basePath
            Path targetPath = Paths.get(basePath).resolve(key);
            return Files.newInputStream(targetPath);
        } catch (IOException e) {
            logger.error("Failed to download file from local storage: {}", key, e);
            throw new RuntimeException("Local storage download failed", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path targetPath = Paths.get(basePath).resolve(key);
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            logger.error("Failed to delete file from local storage: {}", key, e);
        }
    }
}
