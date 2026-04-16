package com.company.video_service.service;

import com.company.video_service.dto.VideoMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

@Service
public class VideoThumbnailService {

    @Value("${video.ffmpeg.path}")
    private String ffmpegPath;

    @Value("${video.ffprobe.path}")
    private String ffprobePath;

    public VideoMetadata generateAndSaveThumbnail(File videoFile, File thumbnailFile) throws Exception {
        // 1. Extract Metadata using ffprobe
        ProcessBuilder probeBuilder = new ProcessBuilder(
                ffprobePath,
                "-v", "error",
                "-show_entries", "format=duration:stream=width,height",
                "-of", "default=noprint_wrappers=1",
                videoFile.getAbsolutePath()
        );
        
        Process probeProcess = probeBuilder.start();
        Long duration = 0L;
        Integer width = 0;
        Integer height = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(probeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("duration=")) {
                    duration = (long) Double.parseDouble(line.split("=")[1]);
                } else if (line.startsWith("width=")) {
                    width = Integer.parseInt(line.split("=")[1]);
                } else if (line.startsWith("height=")) {
                    height = Integer.parseInt(line.split("=")[1]);
                }
            }
        }
        probeProcess.waitFor();

        // 2. Generate Thumbnail using ffmpeg
        ProcessBuilder thumbBuilder = new ProcessBuilder(
                ffmpegPath,
                "-i", videoFile.getAbsolutePath(),
                "-ss", "00:00:01",
                "-vframes", "1",
                "-q:v", "2",
                thumbnailFile.getAbsolutePath()
        );
        thumbBuilder.start().waitFor();

        VideoMetadata metadata = new VideoMetadata(duration, width, height);
        metadata.setThumbnailPath(thumbnailFile.getAbsolutePath());
        return metadata;
    }
}
