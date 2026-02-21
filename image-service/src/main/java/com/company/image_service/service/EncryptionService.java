package com.company.image_service.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public interface EncryptionService {

    /**
     * Encrypts the provided input stream and writes it to the destination path.
     * Uses AES-256 in GCM mode.
     */
    void encryptAndSave(InputStream inputStream, Path destinationPath) throws Exception;

    /**
     * Reads an encrypted file from the source path, decrypts it in memory,
     * and writes the raw bytes directly to the provided output stream.
     */
    void decryptToStream(Path sourcePath, OutputStream outputStream) throws Exception;

}
