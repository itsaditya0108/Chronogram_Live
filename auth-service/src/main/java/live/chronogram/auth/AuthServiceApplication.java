package live.chronogram.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Authentication Service.
 * This service handles user authentication, OTP verification, and session
 * management.
 */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class AuthServiceApplication {

    @jakarta.annotation.PostConstruct
    public void init() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
    }

    /**
     * Starts the Spring Boot application.
     * 
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}
