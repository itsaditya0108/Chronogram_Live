package com.company.image_service.service.storage;

import com.company.image_service.dto.StoredImageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.awt.image.BufferedImage;
import java.io.InputStream;


@Service
@Primary
public class S3StorageService implements FileStorageService {

    private final S3Client s3Client;

    @Autowired
    private com.company.image_service.service.EncryptionService encryptionService;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.base-path:users}")
    private String basePath;

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public StoredImageResult store(InputStream stream, String originalFilename, Long userId, String type, BufferedImage source) {
        try {
            String extension = getExtension(originalFilename);
            String uuid = java.util.UUID.randomUUID().toString();
            String filename = uuid + extension + ".enc";
            String thumbFilename = uuid + extension + ".enc.thumb";

            String subfolder = "chat".equalsIgnoreCase(type) ? "shared_images" : userId + "/images";
            String baseKey = basePath + "/" + subfolder;
            java.time.LocalDate now = java.time.LocalDate.now();
            String datePath = now.getYear() + "/" + String.format("%02d", now.getMonthValue());

            String originalKey = baseKey + "/" + datePath + "/original/" + filename;
            String thumbKey = baseKey + "/" + datePath + "/thumb/" + thumbFilename;

            // 1. Save ORIGINAL (Encrypted) to S3
            // We need to encrypt into a buffer first because S3 SDK needs length or a specific RequestBody
            java.io.ByteArrayOutputStream originalBaos = new java.io.ByteArrayOutputStream();
            encryptionService.encryptAndSave(stream, originalBaos);
            byte[] originalData = originalBaos.toByteArray();

            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(originalKey).contentType("application/octet-stream").build(),
                    RequestBody.fromBytes(originalData)
            );

            // 2. Save THUMBNAIL (Encrypted) to S3
            int width = 0;
            int height = 0;
            String relativeThumbPath = null;
            byte[] thumbData = null;

            if (source != null) {
                width = source.getWidth();
                height = source.getHeight();

                int newWidth, newHeight;
                int thumbSize = 200;
                if (width > height) {
                    newWidth = thumbSize;
                    newHeight = (height * thumbSize) / width;
                } else {
                    newHeight = thumbSize;
                    newWidth = (width * thumbSize) / height;
                }
                if (newWidth <= 0) newWidth = 1;
                if (newHeight <= 0) newHeight = 1;

                BufferedImage thumb = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = thumb.createGraphics();
                g.drawImage(source, 0, 0, newWidth, newHeight, null);
                g.dispose();

                java.io.ByteArrayOutputStream thumbBaos = new java.io.ByteArrayOutputStream();
                com.company.image_service.util.ImageCompressionUtil.writeCompressedImage(thumb, "jpg", 0.2f, thumbBaos);
                
                java.io.ByteArrayOutputStream thumbEncBaos = new java.io.ByteArrayOutputStream();
                encryptionService.encryptAndSave(new java.io.ByteArrayInputStream(thumbBaos.toByteArray()), thumbEncBaos);
                
                thumbData = thumbEncBaos.toByteArray();
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(bucket).key(thumbKey).contentType("application/octet-stream").build(),
                        RequestBody.fromBytes(thumbData)
                );
                relativeThumbPath = thumbKey;
            }

            long totalSize = originalData.length + (thumbData != null ? thumbData.length : 0);
            return new StoredImageResult(filename, originalKey, relativeThumbPath, width, height, totalSize);

        } catch (Exception e) {
            throw new RuntimeException("S3 Storage failed", e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot).toLowerCase();
    }

    @Override
    public InputStream download(String key) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return s3Client.getObject(request);
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
        s3Client.deleteObject(request);
    }
}