package com.company.image_service.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

public class ImageCompressionUtil {

    /**
     * Writes an image with explicit compression quality to reduce file size.
     * Supports only JPG/JPEG formats efficiently. Falls back to default ImageIO write if unable to set quality.
     *
     * @param image   The BufferedImage to write
     * @param format  The format string (e.g., "jpg", "png")
     * @param quality The compression quality (0.0f to 1.0f). 1.0 is highest quality (largest size), 0.0 is lowest.
     * @param os      The OutputStream to write the image to
     * @throws IOException If writing fails
     */
    public static void writeCompressedImage(BufferedImage image, String format, float quality, OutputStream os) throws IOException {
        String effectiveFormat = (format != null && !format.trim().isEmpty()) ? format.toLowerCase() : "jpg";
        
        // PNG doesn't support ImageWriteParam quality compression out of the box easily. Fall back to standard saving if strictly png.
        // However, we generally force jpg for thumbnails or compress them as jpg.
        if (effectiveFormat.equals("png")) {
            // Because PNG is lossless, compression quality setting isn't supported the same way.
            ImageIO.write(image, "png", os);
            return;
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(effectiveFormat);
        if (!writers.hasNext()) {
            writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IOException("No writer found for format: " + effectiveFormat);
            }
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
