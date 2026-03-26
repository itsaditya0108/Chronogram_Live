package com.company.image_service.mapper;

import com.company.image_service.dto.ImageResponseDto;
import com.company.image_service.dto.ApprovedFormatResponseDto;
import com.company.image_service.entity.Image;
import java.util.Collections;
import java.util.ArrayList;

public class ImageMapper {

    public static ImageResponseDto toDto(Image image) {

        ImageResponseDto dto = new ImageResponseDto();
        dto.setId(image.getId());
        dto.setOriginalFilename(image.getOriginalFilename());
        dto.setContentType(image.getContentType());
        dto.setFileSize(image.getFileSize());
        dto.setCreatedAt(image.getCreatedTimestamp());
        dto.setImageUrl("/api/images/" + image.getId() + "/download");
        dto.setThumbnailUrl("/api/images/" + image.getId() + "/thumbnail");
        dto.setWidth(image.getWidth());
        dto.setHeight(image.getHeight());


        return dto;
    }

    // Variant DTO mapping removed as per user request (no per-file variants in DB)
}
