package com.company.image_service.util;

import com.company.image_service.dto.StoredImageResult;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

public class FileStorageUtil {

        private static final int THUMB_SIZE = 200;

        public static StoredImageResult storeWithThumbnail(
                        MultipartFile file,
                        Long userId,
                        String basePath,
                        BufferedImage source,
                        String type,
                        com.company.image_service.service.EncryptionService encryptionService) throws Exception {
                return storeWithThumbnail(
                                file.getInputStream(),
                                file.getOriginalFilename(),
                                userId,
                                basePath,
                                source,
                                type,
                                encryptionService);
        }

        public static StoredImageResult storeWithThumbnail(
                        java.io.InputStream inputStream,
                        String originalFilename,
                        Long userId,
                        String basePath,
                        BufferedImage source,
                        String type,
                        com.company.image_service.service.EncryptionService encryptionService) throws Exception {

                LocalDate now = LocalDate.now();

                String extension = getExtension(originalFilename);
                String uuid = UUID.randomUUID().toString();
                String filename = uuid + extension + ".enc";
                String thumbFilename = uuid + extension + ".enc.thumb";

                // -------------------------
                // Directories
                // -------------------------
                Path baseDir;

                if ("chat".equalsIgnoreCase(type)) {
                        baseDir = Paths.get(
                                        basePath,
                                        "shared_images",
                                        String.valueOf(now.getYear()),
                                        String.format("%02d", now.getMonthValue()));
                } else {
                        baseDir = Paths.get(
                                        basePath,
                                        "users",
                                        userId.toString(),
                                        String.valueOf(now.getYear()),
                                        String.format("%02d", now.getMonthValue()));
                }

                Path originalDir = baseDir.resolve("images");
                Path thumbDir = baseDir.resolve("thumbnail");

                Files.createDirectories(originalDir);
                Files.createDirectories(thumbDir);

                // -------------------------
                // Save ORIGINAL (Encrypted)
                // -------------------------
                Path originalPath = originalDir.resolve(filename);
                encryptionService.encryptAndSave(inputStream, originalPath);

                // -------------------------
                // Create & Save THUMBNAIL (Encrypted) - Only if source is available
                // -------------------------
                int width = 0;
                int height = 0;
                String relativeThumbPath = null;

                if (source != null) {
                        width = source.getWidth();
                        height = source.getHeight();
                        
                        int newWidth, newHeight;
                        if (width > height) {
                            newWidth = THUMB_SIZE;
                            newHeight = (height * THUMB_SIZE) / width;
                        } else {
                            newHeight = THUMB_SIZE;
                            newWidth = (width * THUMB_SIZE) / height;
                        }
                        
                        if (newWidth <= 0) newWidth = 1;
                        if (newHeight <= 0) newHeight = 1;

                        BufferedImage thumb = new BufferedImage(
                                        newWidth,
                                        newHeight,
                                        BufferedImage.TYPE_INT_RGB);

                        Graphics2D g = thumb.createGraphics();
                        g.setRenderingHint(
                                        RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.setRenderingHint(
                                        RenderingHints.KEY_RENDERING,
                                        RenderingHints.VALUE_RENDER_QUALITY);
                        g.setRenderingHint(
                                        RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        // Fill with white background first (for transparent images converted to RGB)
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, newWidth, newHeight);
                        
                        g.drawImage(source, 0, 0, newWidth, newHeight, null);
                        g.dispose();

                        Path thumbPath = thumbDir.resolve(thumbFilename);

                        // Convert thumb to bytes for encryption
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        
                        try {
                            com.company.image_service.util.ImageCompressionUtil.writeCompressedImage(thumb, "jpg", 0.2f, baos);
                        } catch (Exception e) {
                            try {
                                com.company.image_service.util.ImageCompressionUtil.writeCompressedImage(thumb, "jpg", 0.2f, baos);
                            } catch (Exception ignored) {}
                        }
                        
                        java.io.InputStream thumbIs = new java.io.ByteArrayInputStream(baos.toByteArray());
                        encryptionService.encryptAndSave(thumbIs, thumbPath);
                        relativeThumbPath = toRelativePath(basePath, thumbPath);
                }

                // -------------------------
                // Relative paths for DB
                // -------------------------
                String relativeOriginalPath = toRelativePath(basePath, originalPath);

                long totalSize = Files.size(originalPath);
                if (relativeThumbPath != null) {
                    Path thumbPath = thumbDir.resolve(thumbFilename);
                    if (Files.exists(thumbPath)) {
                        totalSize += Files.size(thumbPath);
                    }
                }

                return new StoredImageResult(
                                filename,
                                relativeOriginalPath,
                                relativeThumbPath,
                                width,
                                height,
                                totalSize);
        }

        private static String toRelativePath(String basePath, Path fullPath) {
                return Paths.get(basePath)
                                .relativize(fullPath)
                                .toString()
                                .replace("\\", "/");
        }

        private static String getExtension(String filename) {
                if (filename == null)
                        return "";
                int dot = filename.lastIndexOf('.');
                if (dot == -1)
                        return "";
                return filename.substring(dot).toLowerCase();
        }
}
