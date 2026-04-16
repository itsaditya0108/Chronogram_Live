package com.company.video_service.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;

@Service
@ConditionalOnProperty(name = "video.storage.mode", havingValue = "s3")
public class S3StorageService implements FileStorageService {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.access-key}")
    private String accessKey;

    @Value("${aws.secret-key}")
    private String secretKey;

    @Value("${aws.s3.base-path:users}")
    private String basePath;

    private S3Client s3Client;

    private synchronized S3Client getS3Client() {
        if (s3Client == null) {
            s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return s3Client;
    }

    @Override
    public String store(File file, String fileName, Long userId, String type) {
        // Build path: users/{userId}/{type}/{YYYY}/{MM}/original/{fileName}
        java.time.LocalDate now = java.time.LocalDate.now();
        String datePath = now.getYear() + "/" + String.format("%02d", now.getMonthValue());
        
        String key = basePath + "/" + userId + "/" + type + "/" + datePath + "/original/" + fileName;
        
        getS3Client().putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build(), RequestBody.fromFile(file));

        return key; 
    }

    @Override
    public void delete(String path) {
        // Optional: Implement S3 delete logic here if needed
    }

    @Override
    public Resource loadAsResource(String path) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build();
        
        ResponseInputStream<GetObjectResponse> s3InputStream = getS3Client().getObject(getObjectRequest);
        return new InputStreamResource(s3InputStream);
    }
}
