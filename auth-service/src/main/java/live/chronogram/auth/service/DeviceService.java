package live.chronogram.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;
import live.chronogram.auth.model.User;
import live.chronogram.auth.model.UserDevice;
import live.chronogram.auth.repository.UserDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing user devices and their trust status.
 * Handles device registration, metadata updates, and security checks for
 * trusted devices.
 */
@Service
public class DeviceService {

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    /**
     * Registers a new device or updates an existing one for a user.
     * Captures device metadata like OS version, model, and location for security
     * auditing.
     * 
     * @param user        The User entity.
     * @param deviceId    The unique identifier for the device.
     * @param simSerial   The SIM serial hash (for telecom binding).
     * @param pushToken   The token for push notifications (FCM/APN).
     * @param trustDevice Whether to mark this device as "trusted".
     * @return The saved UserDevice entity.
     */
    @Transactional
    public UserDevice registerOrUpdateDevice(User user, String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel,
            String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, boolean trustDevice) {

        // 1. Mandatory Identifier Check
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new RuntimeException("Device ID is required.");
        }

        // 2. Identity Lookup: Check if this device is already known for this user
        Optional<UserDevice> existingDeviceOpt = userDeviceRepository.findByUser_UserIdAndDeviceId(user.getUserId(),
                deviceId);

        UserDevice device;
        if (existingDeviceOpt.isPresent()) {
            // Case A: UPDATE existing device metadata
            device = existingDeviceOpt.get();
            device.setLastLoginTimestamp(LocalDateTime.now());
            device.setDeviceName(deviceName);
            device.setDeviceModel(deviceModel);
            device.setOsName(osName);
            device.setOsVersion(osVersion);
            device.setAppVersion(appVersion);
            device.setLatitude(latitude);
            device.setLongitude(longitude);
            device.setCountry(country);
            device.setCity(city);

            // Conditional Updates: Only update push token/sim serial if provided (to avoid clearing valid data)
            if (pushToken != null && !pushToken.isEmpty()) {
                device.setPushToken(pushToken);
            }
            if (trustDevice) {
                device.setIsTrusted(true);
            }
            if (simSerial != null && !simSerial.isEmpty()) {
                device.setSimSerialHash(simSerial);
            }
        } else {
            // Case B: REGISTER new device entry
            device = new UserDevice();
            device.setUser(user);
            device.setDeviceId(deviceId);
            device.setSimSerialHash(simSerial);
            device.setPushToken(pushToken);
            device.setDeviceName(deviceName);
            device.setDeviceModel(deviceModel);
            device.setOsName(osName);
            device.setOsVersion(osVersion);
            device.setAppVersion(appVersion);
            device.setIsTrusted(trustDevice);
            device.setFirstInstallTimestamp(LocalDateTime.now());
            device.setLastLoginTimestamp(LocalDateTime.now());
            device.setLatitude(latitude);
            device.setLongitude(longitude);
            device.setCountry(country);
            device.setCity(city);
        }

        // 3. Persist the updated/new device record
        return userDeviceRepository.save(device);
    }

    /**
     * Checks if a specific device ID is marked as trusted for a given user.
     * Trusted devices bypass secondary OTP layers in many flows.
     * 
     * @return true if trusted, false if unrecognized or explicitly untrusted.
     */
    public boolean isDeviceTrusted(User user, String deviceId) {
        return userDeviceRepository.findByUser_UserIdAndDeviceId(user.getUserId(), deviceId)
                .map(UserDevice::getIsTrusted)
                .orElse(false);
    }

    /**
     * Checks if a user has at least one trusted device registered.
     */
    public boolean hasAnyTrustedDevice(Long userId) {
        return userDeviceRepository.findByUser_UserId(userId).stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getIsTrusted()));
    }
}
