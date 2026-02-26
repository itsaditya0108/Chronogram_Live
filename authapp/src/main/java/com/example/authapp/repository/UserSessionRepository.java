package com.example.authapp.repository;

import com.example.authapp.entity.User;
import com.example.authapp.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository
    extends JpaRepository<UserSession, Long> {

  Optional<UserSession> findByRefreshTokenHashAndIsRevokedFalse(
      String refreshTokenHash);

    @Transactional
    @Modifying
    @Query("""
        UPDATE UserSession s
        SET s.isRevoked = true
        WHERE s.user.id = :userId
          AND (s.isRevoked = false OR s.isRevoked IS NULL)
    """)
    int revokeAllSessions(@Param("userId") Long userId);


    @Modifying
  @Query("""
          UPDATE UserSession s
          SET s.isRevoked = true
          WHERE s.user.id = :userId
            AND s.sessionId <> :currentSessionId
            AND (s.isRevoked = false OR s.isRevoked IS NULL)
      """)
  int revokeAllOtherSessions(@Param("userId") Long userId, @Param("currentSessionId") Long currentSessionId);

  @Query("""
          SELECT s FROM UserSession s
          WHERE s.isRevoked = false
            AND s.refreshTokenHash IS NOT NULL
      """)
  List<UserSession> findAllActiveSessions();

  @Query("""
          SELECT s FROM UserSession s
          WHERE s.user.id = :userId
            AND s.isRevoked = false
      """)
  List<UserSession> findActiveSessions(@Param("userId") Long userId);


}
