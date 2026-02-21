package com.company.image_service; // Package declaration for the Image Service application

import org.springframework.boot.SpringApplication; // Spring Boot application launcher
import org.springframework.boot.autoconfigure.SpringBootApplication; // Auto-configuration annotation
import org.springframework.scheduling.annotation.EnableScheduling; // Scheduling annotation

@EnableScheduling // Enables Spring's scheduled task execution capability
@SpringBootApplication // Marks this class as the primary configuration and entry point
public class ImageServiceApplication { // Main class for the Image Service

	// The main method serves as the entry point for the Java application
	public static void main(String[] args) {
		// Launches the Spring Boot application, creating the ApplicationContext
		SpringApplication.run(ImageServiceApplication.class, args);
	}
}
