package live.chronogram.auth.repository;

import java.util.List;
import live.chronogram.auth.model.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for AdminAuditLog entity.
 * Records actions taken by administrators for accountability and security
 * auditing.
 */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    /**
     * Retrieves audit logs for actions performed by a specific admin.
     */
    List<AdminAuditLog> findByAdmin_AdminIdOrderByCreatedTimestampDesc(Long adminId);

    /**
     * Retrieves audit logs for actions performed on a specific target user.
     */
    List<AdminAuditLog> findByTargetUserIdOrderByCreatedTimestampDesc(Long targetUserId);
}
