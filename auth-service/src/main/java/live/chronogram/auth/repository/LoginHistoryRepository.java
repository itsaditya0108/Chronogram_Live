package live.chronogram.auth.repository;

import java.util.List;
import live.chronogram.auth.model.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for LoginHistory entity.
 * Provides an audit trail of successful and failed login attempts.
 */
@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    /**
     * Retrieves login history for a specific user, ordered by most recent first.
     */
    List<LoginHistory> findByUserIdOrderByCreatedTimestampDesc(Long userId);

    /**
     * Retrieves login history originating from a specific IP address.
     */
    List<LoginHistory> findByIpAddressOrderByCreatedTimestampDesc(String ipAddress);
}
