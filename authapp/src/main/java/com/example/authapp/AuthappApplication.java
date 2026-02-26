package com.example.authapp;

import com.example.authapp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AuthappApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(AuthappApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(AuthappApplication.class, args);
    }


}



//    @Bean
//    CommandLineRunner test(UserRepository repo) {
//        return args -> {
//            System.out.println("User count: " + repo.count());
//        };
//    }


