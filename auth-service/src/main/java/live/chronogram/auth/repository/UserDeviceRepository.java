package live.chronogram.auth.repository;

import java.util.List;
import java.util.Optional;
import live.chronogram.auth.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for UserDevice entity.
 * Manages device metadata and trust status for users.
 */
@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    /**
     * Finds a device by its unique device identifier.
     */
    Optional<UserDevice> findByDeviceId(String deviceId);

    /**
     * Retrieves all devices associated with a specific user.
     */
    List<UserDevice> findByUser_UserId(Long userId);

    /**
     * Finds a specific device belonging to a specific user.
     */
    Optional<UserDevice> findByUser_UserIdAndDeviceId(Long userId, String deviceId);
}
