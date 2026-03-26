package com.company.image_service.util;

import java.security.MessageDigest;

public class HashUtil {

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA256_ERROR", e);
        }
    }

    public static String sha256Hex(java.io.InputStream is) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; // 64KB buffer
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hash = digest.digest();
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA256_STREAM_ERROR", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
