package com.company.image_service.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proactive database migration component to fix Hibernate versioning issues
 * caused by existing data having NULL in the @Version column.
 */
@Component
public class DatabaseMigrationComponent implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationComponent.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            logger.info("Starting proactive database migration: Fixing NULL version values...");
            int updatedCount = entityManager.createNativeQuery(
                "UPDATE upload_sessions SET version = 0 WHERE version IS NULL"
            ).executeUpdate();
            
            if (updatedCount > 0) {
                logger.info("Database migration successful: Fixed {} NULL version values in upload_sessions.", updatedCount);
            } else {
                logger.info("Database migration: No NULL version values found in upload_sessions.");
            }
        } catch (Exception e) {
            // Log as warning since the table might not exist yet during first-time setup
            logger.warn("Native migration skipped or failed: {}", e.getMessage());
        }
    }
}
