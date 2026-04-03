package live.chronogram.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Configuration for Asynchronous execution in Spring.
 * Enables the @Async annotation used for non-blocking operations like sending emails.
 * Also registers a global uncaught exception handler so SMTP failures are
 * never silently swallowed — they will always appear in the application logs.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Dedicated thread pool for email tasks.
     * Prevents long-running SMTP calls from blocking core application threads.
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("EmailThread-");
        executor.initialize();
        return executor;
    }

    /**
     * Captures any exception thrown from an @Async method and logs it loudly.
     * Without this, SMTP failures are silently discarded by the JVM.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            logger.error("========================================");
            logger.error("!!! ASYNC EMAIL TASK FAILED !!!");
            logger.error("Method: {}", method.getName());
            logger.error("Exception Type: {}", ex.getClass().getName());
            logger.error("Message: {}", ex.getMessage());
            if (ex.getCause() != null) {
                logger.error("Root Cause: {}", ex.getCause().getMessage());
            }
            logger.error("Full Stack Trace:", ex);
            logger.error("========================================");
        };
    }
}
