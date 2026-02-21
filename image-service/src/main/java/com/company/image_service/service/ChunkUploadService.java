package com.company.image_service.service;

import com.company.image_service.entity.UploadSession;
import org.springframework.web.multipart.MultipartFile;

public interface ChunkUploadService {

    /**
     * Initializes a new Upload Session for chunked uploads.
     */
    UploadSession initiateUpload(Long userId, String originalFilename, int totalChunks, long totalFileSize,
            Long syncSessionId);

    /**
     * Receives and saves a specific chunk for an upload session.
     * Returns true if this was the last chunk and the merging process should begin.
     */
    boolean receiveChunk(String uploadId, MultipartFile chunk, int chunkIndex);

    /**
     * Retrieves session details based on uploadId.
     */
    UploadSession getSessionStatus(String uploadId);
}
