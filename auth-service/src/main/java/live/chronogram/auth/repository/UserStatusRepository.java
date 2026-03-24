package live.chronogram.auth.repository;

import live.chronogram.auth.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for UserStatus entity.
 * Provides access to lookup data for user account statuses (e.g., ACTIVE,
 * BLOCKED).
 */
@Repository
public interface UserStatusRepository extends JpaRepository<UserStatus, String> {
}
