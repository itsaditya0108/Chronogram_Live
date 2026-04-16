package com.company.video_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
public class VideoServiceApplication {

    @PostConstruct
    public void init() {
        // Setting Spring Boot SetTimeZone to IST
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        System.out.println("VideoService Application started in Asia/Kolkata time zone: " + new java.util.Date());
    }

    public static void main(String[] args) {
        SpringApplication.run(VideoServiceApplication.class, args);
    }
}
