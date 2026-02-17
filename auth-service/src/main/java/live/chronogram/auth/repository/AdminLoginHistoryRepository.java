package live.chronogram.auth.repository;

import java.util.List;
import live.chronogram.auth.model.AdminLoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminLoginHistoryRepository extends JpaRepository<AdminLoginHistory, Long> {
    List<AdminLoginHistory> findByAdmin_AdminIdOrderByCreatedTimestampDesc(Long adminId);
}
