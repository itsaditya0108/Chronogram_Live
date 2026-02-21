package com.company.image_service.service;

public interface MergeWorkerService {

    /**
     * Asynchronously merges chunks, calculates the hash, checks for duplicates,
     * encrypts the final file, and saves the Image record.
     */
    void processUploadSessionAsync(String uploadId);
}
