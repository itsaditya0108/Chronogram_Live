package live.chronogram.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;
import live.chronogram.auth.model.User;
import live.chronogram.auth.model.UserDevice;
import live.chronogram.auth.repository.UserDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    /**
     * Registers a new device for a user, or updates an existing device's metadata
     * (such as last login, app version, OS version, or location).
     * 
     * @param user        The User entity.
     * @param deviceId    The unique hardware/software identifier for the device.
     * @param simSerial   The hashed SIM serial (important for telecom/fintech
     *                    strict binding).
     * @param pushToken   The FCM or APNs token for push notifications.
     * @param trustDevice Whether this device has successfully passed all
     *                    verification hurdles
     *                    and should be considered "trusted" for future logins
     *                    without strict OTPs.
     * @return The saved UserDevice entity.
     */
    @Transactional
    public UserDevice registerOrUpdateDevice(User user, String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel,
            String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, boolean trustDevice) {

        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new RuntimeException("Device ID is required.");
        }

        Optional<UserDevice> existingDeviceOpt = userDeviceRepository.findByUser_UserIdAndDeviceId(user.getUserId(),
                deviceId);

        UserDevice device;
        if (existingDeviceOpt.isPresent()) {
            device = existingDeviceOpt.get();
            device.setLastLoginTimestamp(LocalDateTime.now());
            // Update metadata
            device.setDeviceName(deviceName);
            device.setDeviceModel(deviceModel);
            device.setOsName(osName);
            device.setOsVersion(osVersion);
            device.setAppVersion(appVersion);
            device.setLatitude(latitude);
            device.setLongitude(longitude);
            device.setCountry(country);
            device.setCity(city);
            // Update Push Token
            if (pushToken != null && !pushToken.isEmpty()) {
                device.setPushToken(pushToken);
            }
            if (trustDevice) {
                device.setIsTrusted(true);
            }
            if (simSerial != null && !simSerial.isEmpty()) {
                device.setSimSerialHash(simSerial); // In real world, hash this!
            }
        } else {
            device = new UserDevice();
            device.setUser(user);
            device.setDeviceId(deviceId);
            device.setSimSerialHash(simSerial); // In real world, hash this!
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

        return userDeviceRepository.save(device);
    }

    /**
     * Checks if a specific device ID is marked as trusted for a given user.
     * Trusted devices often bypass secondary 2FA prompts in subsequent logins.
     */
    public boolean isDeviceTrusted(User user, String deviceId) {
        return userDeviceRepository.findByUser_UserIdAndDeviceId(user.getUserId(), deviceId)
                .map(UserDevice::getIsTrusted)
                .orElse(false);
    }

    /**
     * Checks if the user has *any* trusted device. Useful for determining if a
     * login
     * is occurring on a "First Device" vs a "Secondary Device" (which triggers
     * security alerts).
     */
    public boolean hasAnyTrustedDevice(Long userId) {
        return userDeviceRepository.findByUser_UserId(userId).stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getIsTrusted()));
    }
}
