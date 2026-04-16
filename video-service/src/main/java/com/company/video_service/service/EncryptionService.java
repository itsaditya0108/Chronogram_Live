package com.company.video_service.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public interface EncryptionService {

    /**
     * Encrypts the input stream and saves the result to the destination path.
     * Uses AES/GCM/NoPadding for high security and integrity.
     */
    void encryptAndSave(InputStream inputStream, Path destinationPath) throws Exception;

    /**
     * Encrypts the input stream and writes it to the output stream.
     * Generates a unique IV and prepends it to the output.
     */
    void encryptAndSave(InputStream inputStream, OutputStream outputStream) throws Exception;

    /**
     * Decrypts a file from sourcePath and writes the raw content to outputStream.
     */
    void decryptToStream(Path sourcePath, OutputStream outputStream) throws Exception;

    /**
     * Decrypts an encrypted input stream and writes the raw content to outputStream.
     * Expects the first 12 bytes to be the GCM IV.
     */
    void decryptToStream(InputStream encryptedInputStream, OutputStream outputStream) throws Exception;
}
