package com.company.image_service.util;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class ProfilePictureStorageUtil {

    private static final int SMALL = 64;
    private static final int MEDIUM = 256;

    public static StoredProfilePicture store(
            MultipartFile file,
            Long userId,
            String basePath
    ) throws IOException {

        String ext = getExtension(file.getOriginalFilename());
        String uuid = UUID.randomUUID().toString();

        Path baseDir = Paths.get(
                basePath,
                "users",
                userId.toString(),
                "profile"
        );

        Path originalDir = baseDir.resolve("original");
        Path smallDir = baseDir.resolve("small");
        Path mediumDir = baseDir.resolve("medium");

        Files.createDirectories(originalDir);
        Files.createDirectories(smallDir);
        Files.createDirectories(mediumDir);

        BufferedImage src = ImageIO.read(file.getInputStream());
        if (src == null) {
            throw new RuntimeException("Invalid image");
        }

        int size = Math.min(src.getWidth(), src.getHeight());
        BufferedImage square = src.getSubimage(
                (src.getWidth() - size) / 2,
                (src.getHeight() - size) / 2,
                size,
                size
        );

        Path originalPath = originalDir.resolve(uuid + ext);
        String format = (ext != null && ext.length() > 1) ? ext.substring(1) : "jpg";
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(originalPath.toFile())) {
            ImageCompressionUtil.writeCompressedImage(square, format, 0.75f, fos);
        }

        Path smallPath = smallDir.resolve(uuid + ext);
        Path mediumPath = mediumDir.resolve(uuid + ext);

        writeResized(square, SMALL, smallPath, ext, 0.15f);
        writeResized(square, MEDIUM, mediumPath, ext, 0.3f);

        return new StoredProfilePicture(
                rel(basePath, originalPath),
                rel(basePath, smallPath),
                rel(basePath, mediumPath),
                square.getWidth(),
                square.getHeight()
        );
    }

    private static void writeResized(
            BufferedImage src,
            int size,
            Path dest,
            String ext,
            float quality
    ) throws IOException {

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();

        String format = (ext != null && ext.length() > 1) ? ext.substring(1) : "jpg";
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest.toFile())) {
            ImageCompressionUtil.writeCompressedImage(img, format, quality, fos);
        }
    }

    private static String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? "" : filename.substring(dot).toLowerCase();
    }

    private static String rel(String base, Path full) {
        return Paths.get(base).relativize(full).toString().replace("\\", "/");
    }

    public record StoredProfilePicture(
            String originalPath,
            String smallPath,
            String mediumPath,
            int width,
            int height
    ) {}
}
