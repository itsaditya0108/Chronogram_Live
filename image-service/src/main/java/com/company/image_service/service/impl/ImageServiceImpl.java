package com.company.image_service.service.impl;

import com.company.image_service.dto.StoredImageResult;
import com.company.image_service.entity.Image;
import com.company.image_service.repository.ImageRepository;
import com.company.image_service.service.ImageService;
import com.company.image_service.util.FileStorageUtil;
import com.company.image_service.util.ImageValidationUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.company.image_service.service.EncryptionService;
import org.springframework.core.io.UrlResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional(readOnly = true)
public class ImageServiceImpl implements ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageServiceImpl.class);

    private final ImageRepository imageRepository;
    private final EncryptionService encryptionService;
    private final String storageBasePath;
    private final String chatStoragePath;
    private final long maxUploadSize;
    private final int maxUploadCount;

    public ImageServiceImpl(
            ImageRepository imageRepository,
            EncryptionService encryptionService,
            @Value("${image.storage.base-path:./data/image-service}") String storageBasePath,
            @Value("${image.storage.chat-path:./data/chat-images/storage}") String chatStoragePath,
            @Value("${image.upload.max-size:5242880}") long maxUploadSize,
            @Value("${image.upload.max-count:100}") int maxUploadCount) {
        this.imageRepository = imageRepository;
        this.encryptionService = encryptionService;
        this.storageBasePath = (storageBasePath == null || storageBasePath.trim().isEmpty()) ? "./data/image-service"
                : storageBasePath;
        this.chatStoragePath = (chatStoragePath == null || chatStoragePath.trim().isEmpty())
                ? "./data/chat-images/storage"
                : chatStoragePath;
        this.maxUploadSize = maxUploadSize;
        this.maxUploadCount = maxUploadCount;

        logger.info("ImageService initialized with BasePath: {}, ChatPath: {}", this.storageBasePath,
                this.chatStoragePath);
        try {
            Files.createDirectories(Paths.get(this.storageBasePath));
            Files.createDirectories(Paths.get(this.chatStoragePath));
        } catch (IOException e) {
            logger.error("Could not create storage directories", e);
        }
    }

    // ------------------------------------------------------------------
    // READ
    // ------------------------------------------------------------------

    @Override
    public Page<Image> getUserImages(Long userId, Pageable pageable) {
        // Default behavior: show all (or could default to personal, but 'all' is safer
        // for now)
        return getUserImages(userId, "all", pageable);
    }

    @Override
    public Page<Image> getUserImages(Long userId, String type, Pageable pageable) {
        // Filter logic
        if ("personal".equalsIgnoreCase(type)) {
            // "users/" is the prefix for personal images
            return imageRepository.findByUserIdAndIsDeletedFalseAndStoragePathStartingWith(userId, "users/", pageable);
        } else if ("chat".equalsIgnoreCase(type)) {
            // "shared_images/" is the prefix for chat images
            return imageRepository.findByUserIdAndIsDeletedFalseAndStoragePathStartingWith(userId, "shared_images/",
                    pageable);
        } else {
            // "all" or unknown
            return imageRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
        }
    }

    @Override
    public Image getUserImage(Long imageId, Long userId) {
        return imageRepository
                .findByIdAndUserIdAndIsDeletedFalse(imageId, userId)
                .orElseThrow(() -> new RuntimeException("Image not found or access denied"));
    }

    // ------------------------------------------------------------------
    // DELETE (SOFT)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Image softDeleteImage(Long imageId, Long userId) {

        Image image = imageRepository
                .findByIdAndUserIdAndIsDeletedFalse(imageId, userId)
                .orElseThrow(() -> new RuntimeException("Image not found or access denied"));

        image.setIsDeleted(true);
        image.setDeletedTimestamp(LocalDateTime.now());

        return imageRepository.save(image);
    }

    // ------------------------------------------------------------------
    // SINGLE IMAGE UPLOAD (WITH THUMBNAIL)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Image uploadImage(Long userId, MultipartFile file, String type) {

        try {
            // 1️⃣ HARD validation (real image check)
            BufferedImage source = ImageValidationUtil.validateAndRead(file, maxUploadSize);

            // Determine which base path to use
            String currentBasePath = "chat".equalsIgnoreCase(type) ? chatStoragePath : storageBasePath;

            logger.info("DEBUG: Uploading image. Type: {}, Using Path: {}", type, currentBasePath);
            logger.info("DEBUG: Configured Chat Path: {}", chatStoragePath);

            // 2️⃣ Store image + thumbnail
            StoredImageResult result = FileStorageUtil.storeWithThumbnail(
                    file,
                    userId,
                    currentBasePath,
                    source,
                    type);

            // 3️⃣ Build metadata
            Image image = new Image();
            image.setUserId(userId);
            image.setOriginalFilename(file.getOriginalFilename());
            image.setStoredFilename(result.getStoredFilename());
            image.setStoragePath(result.getOriginalPath());
            image.setThumbnailPath(result.getThumbnailPath());
            image.setWidth(result.getWidth());
            image.setHeight(result.getHeight());
            image.setContentType(file.getContentType());
            image.setFileSize(file.getSize());
            image.setIsDeleted(false);

            return imageRepository.save(image);

        } catch (Exception ex) {
            throw new RuntimeException("Image upload failed", ex);
        }
    }

    // ------------------------------------------------------------------
    // MULTI IMAGE UPLOAD (TEMPORARILY DISABLED)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public List<Image> uploadImages(Long userId, List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            throw new RuntimeException("No files provided");
        }

        if (files.size() > maxUploadCount) {
            throw new RuntimeException("Too many images uploaded at once");
        }

        List<StoredImageResult> storedResults = new ArrayList<>();
        List<Image> images = new ArrayList<>();

        try {
            logger.info("Starting bulk upload for user {} with {} files", userId, files.size());

            for (MultipartFile file : files) {

                String originalName = file.getOriginalFilename();

                // 0️⃣ DUPLICATE CHECK
                if (imageRepository.findByUserIdAndOriginalFilenameAndIsDeletedFalse(userId, originalName)
                        .isPresent()) {
                    logger.warn("Skipping duplicate file: {} for user {}", originalName, userId);
                    continue; // Skip this file
                }

                try {
                    // 1️⃣ Validate image FIRST
                    BufferedImage source = ImageValidationUtil.validateAndRead(file, maxUploadSize);

                    // 2️⃣ Store image + thumbnail
                    StoredImageResult result = FileStorageUtil.storeWithThumbnail(
                            file,
                            userId,
                            storageBasePath,
                            source,
                            "personal");

                    storedResults.add(result);

                    // 3️⃣ Build entity
                    Image image = new Image();
                    image.setUserId(userId);
                    image.setOriginalFilename(file.getOriginalFilename());
                    image.setStoredFilename(result.getStoredFilename());
                    image.setStoragePath(result.getOriginalPath());
                    image.setThumbnailPath(result.getThumbnailPath());
                    image.setWidth(result.getWidth());
                    image.setHeight(result.getHeight());
                    image.setContentType(file.getContentType());
                    image.setFileSize(file.getSize());
                    image.setIsDeleted(false);

                    images.add(image);

                } catch (Exception e) {
                    logger.error("Failed to process file during sync: {}", originalName, e);

                }
            }

            // 4️⃣ Save DB records for successful ones
            if (images.isEmpty()) {
                // If EVERYTHING failed, then maybe we should throw?
                // Or just return empty list.
                logger.warn("No valid images processed in batch for user {}", userId);
                return new ArrayList<>();
            }

            return imageRepository.saveAll(images);

        } catch (Exception ex) {

            // This outer catch now only catches unexpected errors OUTSIDE the loop
            // or if saveAll fails.

            // 5️⃣ Rollback filesystem for ANYTHING that was stored
            for (StoredImageResult r : storedResults) {
                deleteQuietly(r.getOriginalPath());
                deleteQuietly(r.getThumbnailPath());
            }

            throw new RuntimeException("Bulk image upload failed: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------
    // DOWNLOAD (ORIGINAL IMAGE)
    // ------------------------------------------------------------------

    @Override
    public Resource downloadImage(Long imageId, Long userId) {

        // 1. Fetch image (checking only existence and deletion first)
        Image image = imageRepository.findById(imageId)
                .filter(img -> !img.getIsDeleted())
                .orElseThrow(() -> new RuntimeException("Image not found"));

        boolean isShared = image.getStoragePath() != null && image.getStoragePath().startsWith("shared_images");

        // 2. Access Control
        if (!isShared) {
            // For personal images, strict ownership check
            if (!image.getUserId().equals(userId)) {
                throw new RuntimeException("Access denied");
            }
        }

        // 3. Resolve Path
        Path fullPath;
        if (isShared) {
            fullPath = Paths.get(chatStoragePath, image.getStoragePath());
        } else {
            fullPath = Paths.get(storageBasePath, image.getStoragePath());
        }

        try {
            Resource resource = new UrlResource(fullPath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("Image file not found");
            }

            return resource;

        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid image path", e);
        }
    }

    // ------------------------------------------------------------------
    // DEV ONLY — used for browser <img> rendering
    // ------------------------------------------------------------------

    public Image getImageById(Long id) {
        return imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));
    }

    public Resource downloadImageById(Long id) {

        Image image = getImageById(id);

        Path fullPath;
        if (image.getStoragePath() != null && image.getStoragePath().startsWith("shared_images")) {
            fullPath = Paths.get(chatStoragePath, image.getStoragePath());
        } else {
            fullPath = Paths.get(storageBasePath, image.getStoragePath());
        }

        try {
            Resource resource = new UrlResource(fullPath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("File not readable");
            }

            return resource;

        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid file path", e);
        }
    }

    // ------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------

    // private void validateSingleFile(MultipartFile file) {
    //
    // if (file == null || file.isEmpty()) {
    // throw new RuntimeException("Empty file");
    // }
    //
    // if (file.getSize() > maxUploadSize) {
    // throw new RuntimeException("File size exceeds limit");
    // }
    //
    // String contentType = file.getContentType();
    // if (contentType == null || !contentType.startsWith("image/")) {
    // throw new RuntimeException("Invalid image type");
    // }
    //
    // }

    // ------------------------------------------------------------------
    // DOWNLOAD THUMBNAIL
    // ------------------------------------------------------------------

    public Resource downloadThumbnail(Long imageId, Long userId) {
        logger.info("DEBUG: downloadThumbnail requested for imageId: {}, userId: {}", imageId, userId);

        // 1. Fetch image
        Image image = imageRepository.findById(imageId)
                .filter(img -> !img.getIsDeleted())
                .orElseThrow(() -> new RuntimeException("Image not found"));

        logger.info("DEBUG: Found image entity. StoragePath: {}, ThumbnailPath: {}, UserId: {}",
                image.getStoragePath(), image.getThumbnailPath(), image.getUserId());

        boolean isShared = image.getThumbnailPath() != null && image.getThumbnailPath().startsWith("shared_images");
        logger.info("DEBUG: isShared: {}", isShared);

        // 2. Access Control
        if (!isShared) {
            if (!image.getUserId().equals(userId)) {
                logger.warn("DEBUG: Access denied. Image belongs to userId: {}, but requested by userId: {}",
                        image.getUserId(), userId);
                throw new RuntimeException("Access denied");
            }
        }

        // Try primary path
        Path fullPath;
        if (isShared) {
            fullPath = Paths.get(chatStoragePath, image.getThumbnailPath());
        } else {
            fullPath = Paths.get(storageBasePath, image.getThumbnailPath());
        }

        logger.info("DEBUG: Resolved full path: {}", fullPath.toAbsolutePath());

        try {
            Resource resource = new UrlResource(fullPath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                logger.warn("DEBUG: Resource NOT FOUND at primary path: {}. Trying fallback path.",
                        fullPath.toAbsolutePath());
            }

            // Fallback: Try the OTHER path
            Path fallbackPath;
            if (isShared) {
                fallbackPath = Paths.get(storageBasePath, image.getThumbnailPath());
            } else {
                fallbackPath = Paths.get(chatStoragePath, image.getThumbnailPath());
            }

            logger.info("DEBUG: Checking fallback path: {}", fallbackPath.toAbsolutePath());

            Resource fallbackResource = new UrlResource(fallbackPath.toUri());
            if (fallbackResource.exists() && fallbackResource.isReadable()) {
                logger.info("DEBUG: Found resource at fallback path!");
                return fallbackResource;
            }

            logger.error("DEBUG: Resource NOT FOUND at either path.");
            throw new RuntimeException("Thumbnail file not found");

        } catch (MalformedURLException e) {
            logger.error("DEBUG: Malformed URL for path: {}", fullPath, e);
            throw new RuntimeException("Invalid thumbnail path", e);
        }
    }

    // ------------------------------------------------------------------
    // SECURE RAM-VIEWING (DECRYPTED STREAM)
    // ------------------------------------------------------------------

    @Override
    public org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody streamDecryptedImage(
            Long imageId, Long userId, boolean isThumbnail) {

        Image image = imageRepository.findById(imageId)
                .filter(img -> !img.getIsDeleted())
                .orElseThrow(() -> new RuntimeException("Image not found"));

        if (!image.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        String targetPathStr = isThumbnail ? image.getThumbnailPath() : image.getStoragePath();
        if (targetPathStr == null) {
            throw new RuntimeException("Path not defined");
        }

        Path absolutePath = Paths.get(storageBasePath, targetPathStr);
        if (!Files.exists(absolutePath)) {
            // Fallback to chat path if applicable
            absolutePath = Paths.get(chatStoragePath, targetPathStr);
            if (!Files.exists(absolutePath)) {
                throw new RuntimeException("Encrypted image file not found on disk");
            }
        }

        final Path finalPath = absolutePath;

        return outputStream -> {
            try {
                // Determine if file is encrypted (.enc) based on our merge worker logic
                if (finalPath.toString().endsWith(".enc") || finalPath.toString().endsWith(".enc.thumb")) {
                    if (encryptionService != null) {
                        encryptionService.decryptToStream(finalPath, outputStream);
                    } else {
                        throw new RuntimeException("Encryption Service not initialized");
                    }
                } else {
                    // It's a legacy unencrypted file (e.g. from before the vault migration)
                    Files.copy(finalPath, outputStream);
                }
            } catch (Exception e) {
                logger.error("Error streaming decrypted image", e);
                // Cannot throw standard exceptions in lambda easily, wrap in IO
                throw new java.io.IOException("Stream failed", e);
            }
        };
    }

    private void deleteQuietly(String relativePath) {
        try {
            Path path = Paths.get(storageBasePath, relativePath);
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean validateImage(Long imageId, Long userId) {
        return imageRepository.findByIdAndUserIdAndIsDeletedFalse(imageId, userId).isPresent();
    }
}