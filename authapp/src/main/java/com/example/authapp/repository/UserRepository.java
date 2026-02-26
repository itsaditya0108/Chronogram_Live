package com.example.authapp.repository;

import com.example.authapp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByResetToken(String resetToken);



    long countByStatus_Id(String id);


    @Query("""
    SELECT u
    FROM User u
    WHERE (
        LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
        OR u.phone LIKE CONCAT('%', :q, '%')
        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
    )
    AND (:statusId IS NULL OR u.status.id = :statusId)
""")
    Page<User> searchUsers(
            @Param("q") String query,
            @Param("statusId") String statusId,
            Pageable pageable
    );


}
