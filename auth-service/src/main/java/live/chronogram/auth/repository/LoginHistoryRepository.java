package live.chronogram.auth.repository;

import java.util.List;
import live.chronogram.auth.model.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findByUserIdOrderByCreatedTimestampDesc(Long userId);

    List<LoginHistory> findByIpAddressOrderByCreatedTimestampDesc(String ipAddress);
}
