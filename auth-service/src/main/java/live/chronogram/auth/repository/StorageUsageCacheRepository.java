package live.chronogram.auth.repository;

import live.chronogram.auth.model.StorageUsageCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageUsageCacheRepository extends JpaRepository<StorageUsageCache, Long> {
}
