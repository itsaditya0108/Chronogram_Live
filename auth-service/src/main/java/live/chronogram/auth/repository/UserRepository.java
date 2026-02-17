package live.chronogram.auth.repository;

import java.util.Optional;
import live.chronogram.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobileNumber(String mobileNumber);

    Optional<User> findByEmail(String email);

    Optional<User> findByResetToken(String resetToken);
}
