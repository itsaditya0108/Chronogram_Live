package com.company.video_service.schedular; // Package for scheduled tasks

import com.company.video_service.entity.VideoProcessingJob; // Job entity
import com.company.video_service.entity.VideoProcessingJobStatus; // Job status enum
import com.company.video_service.repository.VideoProcessingJobRepository; // Repository for jobs
import com.company.video_service.service.VideoMergeService; // Service for processing merge jobs
import com.company.video_service.repository.VideoUploadSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled; // Scheduled annotation
import org.springframework.stereotype.Component; // Component annotation

import java.io.File;
import java.time.LocalDateTime;
import java.util.List; // List interface
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component // Registers this bean as a Spring Component
public class VideoJobScheduler { // Scheduler for background video processing tasks

    private final VideoProcessingJobRepository jobRepository; // Repo to access jobs
    private final VideoMergeService mergeService; // Service to execute merge logic
    private final VideoUploadSessionRepository sessionRepository;

    @Value("${video.storage.temp-path}")
    private String tempStoragePath;

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    // Constructor injection
    public VideoJobScheduler(VideoProcessingJobRepository jobRepository,
            VideoMergeService mergeService,
            VideoUploadSessionRepository sessionRepository) {
        this.jobRepository = jobRepository;
        this.mergeService = mergeService;
        this.sessionRepository = sessionRepository;
    }

    // Runs every 5000ms (5 seconds) after the last execution finishes
    @Scheduled(fixedDelay = 5000)
    public void processMergeJobs() {

        // Fetch top 5 PENDING jobs, ordered by creation time (FCFS)
        List<VideoProcessingJob> jobs = jobRepository
                .findTop5ByStatusOrderByCreatedTimestampAsc(VideoProcessingJobStatus.PENDING);

        // Iterate and process each job via ExecutorService
        for (VideoProcessingJob job : jobs) {
            // Checkpoint job to prevent re-fetching by other scheduler ticks
            job.setStatus(VideoProcessingJobStatus.RUNNING);
            jobRepository.save(job);

            executorService.submit(() -> {
                try {
                    // Delegate processing to the merge service
                    mergeService.processMergeJob(job);
                } catch (Exception e) {
                    System.err.println("Unexpected error processing job " + job.getJobUid() + ": " + e.getMessage());
                }
            });
        }
    }

    @Scheduled(fixedRate = 300000) // run every 5 mins
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<com.company.video_service.entity.VideoUploadSession> expiredSessions = sessionRepository
                .findAllByStatusInAndExpiresTimestampBefore(
                        List.of(com.company.video_service.entity.UploadSessionStatus.INITIATED,
                                com.company.video_service.entity.UploadSessionStatus.UPLOADING),
                        now);

        for (com.company.video_service.entity.VideoUploadSession session : expiredSessions) {
            session.setStatus(com.company.video_service.entity.UploadSessionStatus.FAILED);
            session.setErrorCode("EXPIRED");
            session.setErrorMessage("Session expired before completion.");
            session.setUpdatedTimestamp(now);
            sessionRepository.save(session);

            // Delete temp folder
            File folder = new File(tempStoragePath, session.getUploadUid());
            if (folder.exists()) {
                deleteDir(folder);
            }
        }
    }

    private void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }
}
