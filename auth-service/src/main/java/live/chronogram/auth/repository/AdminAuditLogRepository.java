package live.chronogram.auth.repository;

import java.util.List;
import live.chronogram.auth.model.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findByAdmin_AdminIdOrderByCreatedTimestampDesc(Long adminId);

    List<AdminAuditLog> findByTargetUserIdOrderByCreatedTimestampDesc(Long targetUserId);
}
