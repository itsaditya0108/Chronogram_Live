package live.chronogram.auth.repository;

import java.util.List;
import java.util.Optional;
import live.chronogram.auth.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByDeviceId(String deviceId);

    List<UserDevice> findByUser_UserId(Long userId);

    Optional<UserDevice> findByUser_UserIdAndDeviceId(Long userId, String deviceId);
}
