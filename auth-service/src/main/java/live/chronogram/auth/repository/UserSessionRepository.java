package live.chronogram.auth.repository;

import java.util.List;
import java.util.Optional;
import live.chronogram.auth.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findByUser_UserId(Long userId);

    List<UserSession> findByUserDevice_UserDeviceId(Long userDeviceId);

    void deleteByRefreshTokenHash(String refreshTokenHash);
}
