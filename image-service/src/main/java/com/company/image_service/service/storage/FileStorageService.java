package com.company.image_service.service.storage;

import com.company.image_service.dto.StoredImageResult;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public interface FileStorageService {

    StoredImageResult store(InputStream stream, String originalFilename, Long userId, String type, BufferedImage source);

    InputStream download(String key);

    void delete(String key);
}