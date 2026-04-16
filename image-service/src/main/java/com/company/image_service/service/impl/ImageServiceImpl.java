package com.company.image_service.service.impl;

import com.company.image_service.dto.ImageBulkUploadResponseDto;
import com.company.image_service.dto.ImageResponseDto;
import com.company.image_service.dto.StoredImageResult;
import com.company.image_service.entity.Image;
import com.company.image_service.mapper.ImageMapper;
import com.company.image_service.repository.*;
import com.company.image_service.service.EncryptionService;
import com.company.image_service.service.IImageService;
import com.company.image_service.service.storage.FileStorageService;
import com.company.image_service.util.FileStorageUtil;
import com.company.image_service.util.ImageValidationUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.UUID;

@Service
public class ImageServiceImpl implements IImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageServiceImpl.class);
    private static final java.util.Set<String> MALICIOUS_EXTENSIONS = java.util.Set.of(".exe", ".bat", ".com", ".sh", ".js", ".vbs", ".msi", ".scr", ".pif", ".cmd");

    private final ImageRepository imageRepository;
    private final UploadSessionRepository uploadRepository;
    private final SyncSessionRepository syncSessionRepository;
    private final ApprovedFormatRepository approvedFormatRepository;
    private final EncryptionService encryptionService;
    private final ProfilePictureRepository profilePictureRepository;
    private final FileStorageService fileStorageService;
    
    @Value("${image.storage.base-path:./data/image-service}")
    private String storagePath;

    @Autowired
    public ImageServiceImpl(ImageRepository imageRepository,
                            UploadSessionRepository uploadRepository,
                            SyncSessionRepository syncSessionRepository,
                            ApprovedFormatRepository approvedFormatRepository,
                            EncryptionService encryptionService,
                            ProfilePictureRepository profilePictureRepository, 
                            FileStorageService fileStorageService) {
        this.imageRepository = imageRepository;
        this.uploadRepository = uploadRepository;
        this.syncSessionRepository = syncSessionRepository;
        this.approvedFormatRepository = approvedFormatRepository;
        this.encryptionService = encryptionService;
        this.profilePictureRepository = profilePictureRepository;
        this.fileStorageService = fileStorageService;
    }

    @PostConstruct
    public void init() {
        logger.info("ImageService initialized with storage provider: {}", fileStorageService.getClass().getSimpleName());
    }

    private List<String> getActiveSupportedFormats() {
        return approvedFormatRepository.findAllByIsActiveTrue().stream()
                .map(com.company.image_service.entity.ApprovedFormat::getExtension)
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImageResponseDto> getUserImages(Long userId, Pageable pageable) {
        return getUserImages(userId, "all", pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImageResponseDto> getUserImages(Long userId, String type, Pageable pageable) {
        Page<Image> images;
        // Logic remains same as we filter by path prefix (users/ or shared_images/)
        if ("personal".equalsIgnoreCase(type)) {
            images = imageRepository.findByUserIdAndIsDeletedFalseAndStoragePathStartingWith(userId, "users/", pageable);
        } else if ("chat".equalsIgnoreCase(type)) {
            images = imageRepository.findByUserIdAndIsDeletedFalseAndStoragePathStartingWith(userId, "shared_images/", pageable);
        } else {
            images = imageRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
        }
        return images.map(ImageMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Image getUserImage(Long imageId, Long userId) {
        return imageRepository.findByIdAndUserIdAndIsDeletedFalse(imageId, userId)
                .orElseThrow(() -> new RuntimeException("Image not found"));
    }

    @Override
    public ImageBulkUploadResponseDto executeBulkUpload(Long userId, MultipartFile[] files, String type) {
        int totalFilesParsed = 0;
        int syncedCount = 0;
        int skippedCount = 0;
        int alreadyUploadedCount = 0;
        List<ImageResponseDto> allImages = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (files == null || files.length == 0) throw new RuntimeException("NO_IMAGES_SELECTED");
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) throw new RuntimeException("FILE_EMPTY");
        }

        List<TempUploadItem> itemsToProcess = new ArrayList<>();
        List<String> activeFormats = getActiveSupportedFormats();

        Path tempDir = Paths.get(storagePath, "temp");
        try {
            if (!Files.exists(tempDir)) Files.createDirectories(tempDir);

            for (MultipartFile file : files) {
                String originalName = file.getOriginalFilename();
                if (originalName == null || originalName.trim().isEmpty()) { skippedCount++; errors.add("No name skipped"); continue; }
                String ext = getExtension(originalName).toLowerCase();
                if (MALICIOUS_EXTENSIONS.contains(ext)) throw new RuntimeException("SECURITY_THREAT");
                if (file.getSize() > 15728640) { skippedCount++; errors.add("File '" + originalName + "': Exceeds size limit (15MB)"); continue; }

                if (originalName.toLowerCase().endsWith(".zip")) {
                    try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            if (entry.isDirectory()) continue;
                            String entryName = entry.getName();
                            if (entryName.contains("/")) entryName = entryName.substring(entryName.lastIndexOf('/') + 1);
                            if (entryName.isEmpty()) continue;
                            
                            String entryExt = getExtension(entryName).toLowerCase();
                            if (activeFormats.contains(entryExt)) {
                                totalFilesParsed++;
                                Path tFile = Files.createTempFile(tempDir, "bulk_zip_", ".tmp");
                                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                long entrySize = 0;
                                try (InputStream entryIs = new DigestInputStream(zis, digest);
                                     OutputStream os = Files.newOutputStream(tFile)) {
                                    byte[] buffer = new byte[8192];
                                    int read;
                                    while ((read = entryIs.read(buffer)) != -1) {
                                        os.write(buffer, 0, read);
                                        entrySize += read;
                                    }
                                }
                                if (entrySize > 15728640) {
                                    Files.deleteIfExists(tFile);
                                    skippedCount++;
                                    errors.add("ZIP Entry '" + entryName + "': Exceeds size limit (15MB)");
                                    continue;
                                }
                                itemsToProcess.add(new TempUploadItem(entryName, tFile, "image/" + (entryExt.startsWith(".") ? entryExt.substring(1) : entryExt), bytesToHex(digest.digest()), entrySize));
                            } else { skippedCount++; errors.add("ZIP File '" + entryName + "': Unsupported format '" + entryExt + "'."); }
                        }
                    } catch (IOException e) { skippedCount++; errors.add("ZIP Error: " + e.getMessage()); }
                } else {
                    totalFilesParsed++;
                    if (activeFormats.contains(ext)) {
                        try {
                            Path tFile = Files.createTempFile(tempDir, "bulk_file_", ".tmp");
                            MessageDigest digest = MessageDigest.getInstance("SHA-256");
                            try (InputStream is = new DigestInputStream(file.getInputStream(), digest);
                                 OutputStream os = Files.newOutputStream(tFile)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = is.read(buffer)) != -1) {
                                    os.write(buffer, 0, read);
                                }
                            }
                            itemsToProcess.add(new TempUploadItem(originalName, tFile, file.getContentType(), bytesToHex(digest.digest()), Files.size(tFile)));
                        } catch (IOException e) { skippedCount++; errors.add("Read Error: " + e.getMessage()); }
                    } else { skippedCount++; errors.add("File '" + originalName + "': Unsupported format '" + ext + "'."); }
                }
            }

            if (itemsToProcess.isEmpty() && totalFilesParsed == 0) throw new RuntimeException("NO_VALID_IMAGES_PROVIDED");

            for (TempUploadItem item : itemsToProcess) {
                try {
                    String hash = item.hash;
                    Optional<Image> existing = imageRepository.findFirstByUserIdAndContentHashAndIsDeletedFalseOrderByCreatedTimestampDesc(userId, hash);
                    if (existing.isPresent()) {
                        alreadyUploadedCount++;
                        ImageResponseDto dto = ImageMapper.toDto(existing.get());
                        dto.setStatus("ALREADY_EXISTS");
                        allImages.add(dto);
                        continue;
                    }

                    BufferedImage source;
                    try (InputStream is = Files.newInputStream(item.path)) {
                        if (item.originalName.toLowerCase().endsWith(".svg") || item.originalName.toLowerCase().endsWith(".svgz")) {
                            source = null;
                        } else {
                            source = ImageValidationUtil.validateAndRead(is, item.size, 15728640, item.originalName);
                        }
                    }

                    StoredImageResult result;
                    try (InputStream is = Files.newInputStream(item.path)) {
                        result = fileStorageService.store(is, item.originalName, userId, type, source);
                    }

                    long encryptedTotalSize = result.getTotalEncryptedSize();

                    Image image = new Image();
                    image.setUserId(userId);
                    image.setOriginalFilename(item.originalName);
                    image.setStoredFilename(result.getStoredFilename());
                    image.setStoragePath(result.getOriginalPath()); 
                    image.setThumbnailPath(result.getThumbnailPath() != null ? result.getThumbnailPath() : result.getOriginalPath());
                    image.setWidth(result.getWidth());
                    image.setHeight(result.getHeight());
                    image.setContentType(item.contentType);
                    image.setFileSize(encryptedTotalSize);
                    image.setIsDeleted(false);
                    image.setContentHash(hash);
                    imageRepository.save(image);
                    
                    ImageResponseDto dto = ImageMapper.toDto(image);
                    dto.setStatus("UPLOADED");
                    allImages.add(dto);
                    syncedCount++;

                    // 9. IMMEDIATE CLEANUP ON SUCCESS (Zero disk footprint for verified sync)
                    try {
                        Files.deleteIfExists(item.path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete temp file {} after success: {}", item.path, e.getMessage());
                    }
                } catch (Exception e) {
                    logger.error("Bulk process failed for item: {}", item.originalName, e);
                    skippedCount++;
                    errors.add("File '" + item.originalName + "': " + e.getMessage());
                }
            }
        } catch (Exception mainEx) {
            throw new RuntimeException("Bulk upload initialization failed: " + mainEx.getMessage(), mainEx);
        }

        return new ImageBulkUploadResponseDto(totalFilesParsed, syncedCount, skippedCount, alreadyUploadedCount, allImages, errors);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadImage(Long imageId, Long userId) {
        Image image = getUserImage(imageId, userId);
        try {
            InputStream is = fileStorageService.download(image.getStoragePath());
            // Since we don't have a direct file path anymore, we might need a custom InputStreamResource or handled in Controller
            // For now, I'll return an InputStreamResource (wrapping it in UrlResource is not ideal for S3)
            return new org.springframework.core.io.InputStreamResource(is);
        } catch (Exception e) {
            throw new RuntimeException("Download failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadThumbnail(Long imageId, Long userId) {
        Image image = getUserImage(imageId, userId);
        try {
            InputStream is = fileStorageService.download(image.getThumbnailPath());
            return new org.springframework.core.io.InputStreamResource(is);
        } catch (Exception e) {
            throw new RuntimeException("Thumbnail download failed", e);
        }
    }

    @Override
    public void deleteImage(Long imageId, Long userId) {
        Image image = getUserImage(imageId, userId);
        image.setIsDeleted(true);
        imageRepository.save(image);
        // Note: Actual file deletion is handled by CleanupScheduler usually, 
        // but we can call fileStorageService.delete(image.getStoragePath()) if needed.
    }


    @Override
    @Transactional(readOnly = true)
    public Image getImageById(Long id) {
        return imageRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadImageById(Long id) {
        Image image = getImageById(id);
        try {
            InputStream is = fileStorageService.download(image.getStoragePath());
            return new org.springframework.core.io.InputStreamResource(is);
        } catch (Exception e) {
            throw new RuntimeException("Download failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody streamDecryptedImage(Long imageId, Long userId, boolean isThumbnail) {
        Image image = getUserImage(imageId, userId);
        String relativePath = isThumbnail ? image.getThumbnailPath() : image.getStoragePath();
        
        return outputStream -> {
            try (InputStream is = fileStorageService.download(relativePath)) {
                encryptionService.decryptToStream(is, outputStream);
            } catch (Exception e) {
                logger.error("Streaming decryption failed for image {}", imageId, e);
                throw new IOException(e);
            }
        };
    }

    @Override
    public String resolveVariantPath(Long imageId, String variantType) {
        Image image = imageRepository.findById(imageId).orElseThrow();
        return "thumb".equalsIgnoreCase(variantType) ? image.getThumbnailPath() : image.getStoragePath();
    }

    @Override
    public boolean validateImage(Long imageId, Long userId) {
        return imageRepository.findByIdAndUserIdAndIsDeletedFalse(imageId, userId).isPresent();
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot).toLowerCase();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class TempUploadItem {
        String originalName;
        Path path;
        String contentType;
        String hash;
        long size;
        TempUploadItem(String originalName, Path path, String contentType, String hash, long size) {
            this.originalName = originalName;
            this.path = path;
            this.contentType = contentType;
            this.hash = hash;
            this.size = size;
        }
    }
}
