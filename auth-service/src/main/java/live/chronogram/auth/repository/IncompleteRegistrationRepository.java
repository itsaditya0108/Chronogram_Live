package live.chronogram.auth.repository;

import live.chronogram.auth.model.IncompleteRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IncompleteRegistrationRepository extends JpaRepository<IncompleteRegistration, Long> {
    Optional<IncompleteRegistration> findByMobileNumber(String mobileNumber);
    void deleteByMobileNumber(String mobileNumber);
}
