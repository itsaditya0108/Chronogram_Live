package com.company.image_service.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public interface EncryptionService {

    /**
     * Encrypts the provided input stream and writes it to the destination path.
     */
    void encryptAndSave(InputStream inputStream, Path destinationPath) throws Exception;

    /**
     * Encrypts the provided input stream and writes it to the provided output stream.
     */
    void encryptAndSave(InputStream inputStream, OutputStream outputStream) throws Exception;

    /**
     * Reads an encrypted file from the source path and writes decrypted bytes to the output stream.
     */
    void decryptToStream(Path sourcePath, OutputStream outputStream) throws Exception;

    /**
     * Reads from an encrypted input stream and writes decrypted bytes to the output stream.
     */
    void decryptToStream(InputStream encryptedInputStream, OutputStream outputStream) throws Exception;

}
