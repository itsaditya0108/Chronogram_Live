package live.chronogram.auth.repository;

import java.util.Optional;
import live.chronogram.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Finds a user by their unique mobile number.
     */
    Optional<User> findByMobileNumber(String mobileNumber);

    /**
     * Finds a user by their unique email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their password reset token.
     */
    Optional<User> findByResetToken(String resetToken);

    /**
     * Retrieves lightweight user details for profile response optimization.
     */
    Optional<live.chronogram.auth.dto.UserSummaryProjection> findProjectedByUserId(Long userId);
}
