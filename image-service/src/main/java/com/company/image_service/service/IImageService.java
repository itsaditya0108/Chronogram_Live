package com.company.image_service.service;

import com.company.image_service.dto.ImageBulkUploadResponseDto;
import com.company.image_service.dto.ImageResponseDto;
import com.company.image_service.entity.Image;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface IImageService {

    ImageBulkUploadResponseDto executeBulkUpload(Long userId, MultipartFile[] files, String type);

    Page<ImageResponseDto> getUserImages(Long userId, Pageable pageable);

    Page<ImageResponseDto> getUserImages(Long userId, String type, Pageable pageable);

    Image getUserImage(Long imageId, Long userId);

    Resource downloadImage(Long imageId, Long userId);

    Resource downloadThumbnail(Long imageId, Long userId);

    Image getImageById(Long id);

    Resource downloadImageById(Long id);

    org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody streamDecryptedImage(Long imageId,
            Long userId, boolean isThumbnail);

    String resolveVariantPath(Long imageId, String variantType);

    boolean validateImage(Long imageId, Long userId);

    void deleteImage(Long imageId, Long userId);
}
