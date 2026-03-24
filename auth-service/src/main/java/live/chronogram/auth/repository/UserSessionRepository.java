package live.chronogram.auth.repository;

import java.util.List;
import java.util.Optional;
import live.chronogram.auth.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for UserSession entity.
 * Tracks active refresh tokens and their association with users and devices.
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    /**
     * Finds a session by the hash of its refresh token.
     */
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * Retrieves all sessions for a specific user.
     */
    List<UserSession> findByUser_UserId(Long userId);

    /**
     * Retrieves all sessions originating from a specific device.
     */
    List<UserSession> findByUserDevice_UserDeviceId(Long userDeviceId);

    /**
     * Checks if a session exists for a user that is NOT revoked.
     */
    boolean existsByUser_UserIdAndIsRevoked(Long userId, Boolean isRevoked);

    /**
     * Explicitly deletes a session by its refresh token hash.
     */
    void deleteByRefreshTokenHash(String refreshTokenHash);
}
