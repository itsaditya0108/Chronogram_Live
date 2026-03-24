package live.chronogram.auth.service;

import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

/**
 * Implementation of NotificationService using Firebase Cloud Messaging (FCM).
 * Currently provided as a mock implementation for development.
 */
@Service
public class FcmNotificationService implements NotificationService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FcmNotificationService.class);
    private final com.google.firebase.messaging.FirebaseMessaging firebaseMessaging;

    public FcmNotificationService(com.google.firebase.messaging.FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * Sends a real push notification via FCM.
     */
    @Override
    public void sendNotification(String pushToken, String title, String body, String type, String payload) {
        if (pushToken == null || pushToken.trim().isEmpty()) {
            logger.warn("Attempted to send notification to null or empty push token.");
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(pushToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("type", type)
                    .putData("payload", payload)
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("Successfully sent FCM message: {}. Response: {}", type, response);
        } catch (Exception e) {
            logger.error("Failed to send FCM message [{}]: {}", type, e.getMessage());
        }
    }

    /**
     * Sends a login alert notification via FCM.
     */
    @Override
    public void sendLoginAlert(String pushToken, String deviceName, String location) {
        String title = "New Login Alert";
        String body = "New login detected on " + deviceName + " near " + location;
        sendNotification(pushToken, title, body, "LOGIN_ALERT", "{}");
    }
}

