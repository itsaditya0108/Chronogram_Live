package live.chronogram.auth.service;

/**
 * Interface for sending push notifications and security alerts.
 */
public interface NotificationService {
    /**
     * Sends a general-purpose push notification.
     * 
     * @param pushToken The device's push token.
     * @param title     The notification title.
     * @param body      The notification message.
     * @param type      The type of notification (e.g., LOGIN_APPROVAL_REQUEST).
     * @param payload   Additional JSON metadata for the app to process.
     */
    void sendNotification(String pushToken, String title, String body, String type, String payload);

    /**
     * Sends a specifically formatted login alert for security monitoring.
     * 
     * @param pushToken  The device's push token.
     * @param deviceName Name of the device where login occurred.
     * @param location   Approximate location of the login.
     */
    void sendLoginAlert(String pushToken, String deviceName, String location);
}
