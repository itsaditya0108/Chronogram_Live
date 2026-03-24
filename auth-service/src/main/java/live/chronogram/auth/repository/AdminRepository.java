package live.chronogram.auth.repository;

import java.util.Optional;
import live.chronogram.auth.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Admin entity.
 * Used for authentication and management of administrative accounts.
 */
@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    /**
     * Finds an admin user by their unique username.
     */
    Optional<Admin> findByUsername(String username);

    /**
     * Finds an admin user by their unique email address.
     */
    Optional<Admin> findByEmail(String email);
}
