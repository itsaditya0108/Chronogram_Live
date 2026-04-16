package com.company.video_service.service.storage;

import org.springframework.core.io.Resource;
import java.io.File;

public interface FileStorageService {
    String store(File file, String fileName, Long userId, String type);
    void delete(String path);
    Resource loadAsResource(String path);
}
