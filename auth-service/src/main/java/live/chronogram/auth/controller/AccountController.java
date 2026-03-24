package live.chronogram.auth.controller;

import live.chronogram.auth.model.User;
import live.chronogram.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller for managing user accounts, including sensitive operations like soft deletion.
 */
@RestController
@RequestMapping("/api/account")
@CrossOrigin(origins = "*")
public class AccountController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private live.chronogram.auth.repository.UserStatusRepository userStatusRepository;

    /**
     * API Endpoint: DELETE /api/account
     * Performs a "Soft Delete" on the user's account.
     * Logic: Sets 'isDeleted = true', 'deleted_at' timestamp, and changes status to 'DELETE'.
     *
     * Platform Restriction: Only iOS clients are allowed to call this endpoint.
     * This satisfies Apple App Store review guidelines (account deletion requirement).
     * Flutter MUST send the header: X-Platform: iOS for all iOS builds.
     */
    @DeleteMapping
    public ResponseEntity<?> deleteAccount(Authentication authentication,
            jakarta.servlet.http.HttpServletRequest servletRequest) {

        // 0. iOS Platform Guard (Apple App Store requirement)
        // Primary check: Flutter must send "X-Platform: iOS" on all iOS builds.
        // Fallback check: User-Agent strings typical of iOS (CFNetwork = iOS HTTP stack).
        String platform = servletRequest.getHeader("X-Platform");
        String userAgent = servletRequest.getHeader("User-Agent");
        boolean isIos = (platform != null && platform.equalsIgnoreCase("iOS"))
                || (userAgent != null && (userAgent.contains("iPhone")
                        || userAgent.contains("iPad")
                        || userAgent.contains("CFNetwork")));
        if (!isIos) {
            throw new live.chronogram.auth.exception.AuthException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Account deletion is only available on iOS devices.");
        }

        // 1. Identification: extract userId from current JWT session
        Long userId = Long.parseLong(authentication.getName());

        // 2. Lookup existing user: must exist in DB
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));

        // 3. Soft Delete: Update flags, no physical row removal (audit/recovery safe)
        user.setIsDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userStatusRepository.findById("DELETE").ifPresent(user::setUserStatus);
        user.setStatusReason("User requested account deletion.");

        // 4. Persist
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
    }
}
