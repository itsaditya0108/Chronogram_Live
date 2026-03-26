package com.company.image_service.util;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VariantConfig {

    public static final Map<String, String> APPROVED_VARIANTS = Map.ofEntries(
            Map.entry(".jpg", "JPEG Image"),
            Map.entry(".jpeg", "JPEG Image"),
            Map.entry(".jpe", "JPEG Variant"),
            Map.entry(".jfif", "JPEG File Interchange"),
            Map.entry(".pjpeg", "Progressive JPEG"),
            Map.entry(".png", "Portable Network Graphics"),
            Map.entry(".gif", "GIF (Static & Animated)"),
            Map.entry(".bmp", "Bitmap Image"),
            Map.entry(".webp", "WebP Image"),
            Map.entry(".tif", "TIFF Image"),
            Map.entry(".tiff", "TIFF Image"),
            Map.entry(".ico", "Icon Image"),
            Map.entry(".svg", "Scalable Vector Graphics"),
            Map.entry(".svgz", "Compressed SVG"),
            Map.entry(".heic", "High Efficiency Image"),
            Map.entry(".heif", "High Efficiency Image Format"),
            Map.entry(".avif", "AV1 Image Format"),
            Map.entry(".apng", "Animated PNG"),
            Map.entry(".dib", "Device Independent Bitmap"),
            Map.entry(".cur", "Cursor Image")
    );

    public static Set<String> getExtensions() {
        return APPROVED_VARIANTS.keySet();
    }

    public static String getFormatName(String extension) {
        return APPROVED_VARIANTS.getOrDefault(extension.toLowerCase(), "Unknown Image Variant");
    }
}
