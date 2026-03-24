package live.chronogram.auth.repository;

import live.chronogram.auth.model.StorageUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageUsageRepository extends JpaRepository<StorageUsage, Long> {
}
