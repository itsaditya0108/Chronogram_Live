package live.chronogram.auth.service;

import org.springframework.stereotype.Service;

@Service
public class FcmNotificationService implements NotificationService {

    // TODO: Inject FirebaseApp or FCM Client here
    // private final FirebaseMessaging firebaseMessaging;

    @Override
    public void sendNotification(String pushToken, String title, String body, String type, String payload) {
        if (pushToken == null || pushToken.isEmpty()) {
            return;
        }

        // Mock Implementation
        System.out.println("--------------------------------------------------");
        System.out.println("[FCM MOCK] Sending Notification to: " + pushToken);
        System.out.println("Title: " + title);
        System.out.println("Body: " + body);
        System.out.println("Type: " + type);
        System.out.println("Payload: " + payload);
        System.out.println("--------------------------------------------------");

        // Real implementation would look like:
        /*
         * Message message = Message.builder()
         * .setToken(pushToken)
         * .setNotification(Notification.builder().setTitle(title).setBody(body).build()
         * )
         * .putData("type", type)
         * .putData("payload", payload)
         * .build();
         * FirebaseMessaging.getInstance().send(message);
         */
    }

    @Override
    public void sendLoginAlert(String pushToken, String deviceName, String location) {
        String title = "New Login Alert";
        String body = "New login detected on " + deviceName + " near " + location;
        sendNotification(pushToken, title, body, "LOGIN_ALERT", "{}");
    }
}
