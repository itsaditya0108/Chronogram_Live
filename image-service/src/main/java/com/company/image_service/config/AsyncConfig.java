package com.company.image_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mergeWorkerPool")
    public Executor mergeWorkerPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Minimum threads
        executor.setMaxPoolSize(20); // Maximum concurrent merge/encrypt operations
        executor.setQueueCapacity(500); // Queue up to 500 merges before rejecting
        executor.setThreadNamePrefix("MergeWorker-");
        executor.initialize();
        return executor;
    }
}
