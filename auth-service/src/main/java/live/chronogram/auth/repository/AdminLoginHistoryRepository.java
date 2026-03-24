package live.chronogram.auth.repository;

import java.util.List;
import live.chronogram.auth.model.AdminLoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for AdminLoginHistory entity.
 * Tracks login attempts for administrative accounts.
 */
@Repository
public interface AdminLoginHistoryRepository extends JpaRepository<AdminLoginHistory, Long> {
    /**
     * Retrieves login history for a specific admin, ordered by most recent first.
     */
    List<AdminLoginHistory> findByAdmin_AdminIdOrderByCreatedTimestampDesc(Long adminId);
}
