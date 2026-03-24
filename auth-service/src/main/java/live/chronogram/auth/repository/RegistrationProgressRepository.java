package live.chronogram.auth.repository;

import live.chronogram.auth.model.RegistrationProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistrationProgressRepository extends JpaRepository<RegistrationProgress, Long> {
}
