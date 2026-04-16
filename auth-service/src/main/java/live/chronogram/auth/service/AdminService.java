package live.chronogram.auth.service;

import live.chronogram.auth.exception.AuthException;
import live.chronogram.auth.model.*;
import live.chronogram.auth.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import live.chronogram.auth.dto.TokenResponse;
import live.chronogram.auth.model.Admin;
import live.chronogram.auth.repository.AdminRepository;
import live.chronogram.auth.security.JwtTokenProvider;
import live.chronogram.auth.repository.UserStatusRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.HashMap;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private StorageUsageRepository storageUsageRepository;

    @Autowired
    private SyncStatusRepository syncStatusRepository;

    @Autowired
    private IncompleteRegistrationRepository incompleteRegistrationRepository;

    @Autowired
    private UserStatusRepository userStatusRepository;

    @Autowired
    private RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Value("${image.service.url}")
    private String imageServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${video.service.url}")
    private String videoServiceUrl;

    public List<User> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .toList();
    }

    public List<User> getPendingUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !"ACTIVE".equals(u.getRegistrationStatus()))
                .toList();
    }

    public List<User> getApprovalList() {
        return userRepository.findAll().stream()
                .filter(u -> "PENDING".equals(u.getApprovalStatus()) && u.getName() != null)
                .toList();
    }

    @Transactional
    public void approveUser(Long userId, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));
        user.setApprovalStatus("APPROVED");
        user.setApprovedBy(adminId);
        user.setApprovedAt(LocalDateTime.now());
        userStatusRepository.findById("ACTIVE").ifPresent(user::setUserStatus);
        userRepository.save(user);
    }

    @Transactional
    public void rejectUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));
        user.setApprovalStatus("REJECTED");
        userRepository.save(user);
    }

    @Transactional
    public void blockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));
        user.setIsBlocked(true);
        user.setBlockedAt(LocalDateTime.now());
        userStatusRepository.findById("BLOCK").ifPresent(user::setUserStatus);
        userRepository.save(user);
    }

    @Transactional
    public void unblockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));
        user.setIsBlocked(false);
        user.setBlockedAt(null);
        userStatusRepository.findById("ACTIVE").ifPresent(user::setUserStatus);
        userRepository.save(user);
    }

    @Transactional
    public void softDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found"));
        user.setIsDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userStatusRepository.findById("DELETE").ifPresent(user::setUserStatus);
        userRepository.save(user);
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AdminService.class);

    public TokenResponse login(String username, String password) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials"));

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials");
        }

        if (Boolean.FALSE.equals(admin.getIsActive())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Admin account is inactive");
        }

        String accessToken = jwtTokenProvider.createAccessToken(admin.getAdminId(), "ADMIN", null);
        String refreshToken = jwtTokenProvider.createRefreshToken(admin.getAdminId());
        return new TokenResponse(accessToken, refreshToken, "ADMIN");
    }

    @Transactional
    public void createAdmin(String username, String email, String password, String role) {
        if (adminRepository.findByUsername(username).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "Username already exists");
        }
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(role);
        admin.setIsActive(true);
        adminRepository.save(admin);
    }

    public List<UserDevice> getAllDevices() {
        return userDeviceRepository.findAll();
    }

    public List<StorageUsage> getAllStorageUsage() {
        return storageUsageRepository.findAll();
    }

    public List<SyncStatus> getAllSyncStatus() {
        return syncStatusRepository.findAll();
    }

    public List<IncompleteRegistration> getAllIncompleteRegistrations() {
        return incompleteRegistrationRepository.findAll();
    }

    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }

    @Transactional
    public void syncAllStorage() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
             try {
                // Fetch Image Storage
                long photoBytes = 0;
                try {
                    String imgUrl = imageServiceUrl.replaceAll("/+$", "") + "/internal/storage/user/" + user.getUserId();
                    Map<String, Object> imgRes = restTemplate.getForObject(imgUrl, Map.class);
                    if (imgRes != null && imgRes.get("photoBytes") != null) photoBytes = ((Number) imgRes.get("photoBytes")).longValue();
                } catch (Exception e) { logger.warn("Image sync failed for user {}: {}", user.getUserId(), e.getMessage()); }

                // Fetch Video Storage
                long videoBytes = 0;
                try {
                    String vidUrl = videoServiceUrl.replaceAll("/+$", "") + "/internal/storage/user/" + user.getUserId();
                    logger.info("Syncing video storage from: {}", vidUrl);
                    Map<String, Object> vidRes = restTemplate.getForObject(vidUrl, Map.class);
                    if (vidRes != null) {
                        // video-service uses totalBytes for total storage
                        Object vBytes = vidRes.get("totalBytes");
                        if (vBytes != null) {
                            videoBytes = ((Number) vBytes).longValue();
                        }
                        logger.info("User {}: Video bytes synced = {}", user.getUserId(), videoBytes);
                    }
                } catch (Exception e) { logger.warn("Video sync failed for user {}: {} (URL: {})", user.getUserId(), e.getMessage(), videoServiceUrl); }

                StorageUsage usage = storageUsageRepository.findById(user.getUserId())
                        .orElse(new StorageUsage(user.getUserId()));
                
                usage.setPhotoBytes(photoBytes);
                usage.setVideoBytes(videoBytes);
                usage.setTotalBytes(photoBytes + videoBytes);
                usage.setLastUpdated(LocalDateTime.now());
                storageUsageRepository.save(usage);
                
            } catch (Exception e) {
                logger.error("Failed to sync storage for user {}: {}", user.getUserId(), e.getMessage());
            }
        }
    }
}
