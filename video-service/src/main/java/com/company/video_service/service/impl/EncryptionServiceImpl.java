package com.company.video_service.service.impl;

import com.company.video_service.service.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;

@Service
public class EncryptionServiceImpl implements EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // in bits

    @Value("${encryption.secret-key}")
    private String secretKeyHex;

    private SecretKeySpec getSecretKey() {
        byte[] keyBytes = hexStringToByteArray(secretKeyHex);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    @Override
    public void encryptAndSave(InputStream inputStream, Path destinationPath) throws Exception {
        try (OutputStream os = Files.newOutputStream(destinationPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            encryptAndSave(inputStream, os);
        }
    }

    @Override
    public void encryptAndSave(InputStream inputStream, OutputStream outputStream) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), parameterSpec);

        // Write IV to the beginning of the output stream (First 12 bytes)
        outputStream.write(iv);

        try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
            byte[] buffer = new byte[65536]; // 64KB buffer
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                cos.write(buffer, 0, read);
            }
            cos.flush();
        }
    }

    @Override
    public void decryptToStream(Path sourcePath, OutputStream outputStream) throws Exception {
        try (InputStream is = Files.newInputStream(sourcePath, StandardOpenOption.READ)) {
            decryptToStream(is, outputStream);
        }
    }

    @Override
    public void decryptToStream(InputStream encryptedInputStream, OutputStream outputStream) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        int readBytes = encryptedInputStream.read(iv);
        if (readBytes != GCM_IV_LENGTH) {
            throw new IllegalStateException("Corrupted encrypted stream (missing IV)");
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), parameterSpec);

        try (CipherInputStream cis = new CipherInputStream(encryptedInputStream, cipher)) {
            byte[] buffer = new byte[65536]; // 64KB buffer
            int read;
            while ((read = cis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
