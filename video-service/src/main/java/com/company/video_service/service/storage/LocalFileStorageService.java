package com.company.video_service.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "video.storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${video.storage.final-path}")
    private String finalStoragePath;

    @Override
    public String store(File file, String fileName, Long userId, String type) {
        try {
            File targetDir = new File(finalStoragePath, "users/" + userId + "/" + type);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            File targetFile = new File(targetDir, fileName);
            Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("LOCAL_STORAGE_FAILED", e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(new File(path).toPath());
        } catch (IOException e) {
            // handle error
        }
    }

    @Override
    public Resource loadAsResource(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new RuntimeException("FILE_NOT_FOUND");
        }
        return new FileSystemResource(file);
    }
}
