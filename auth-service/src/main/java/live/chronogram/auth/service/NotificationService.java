package live.chronogram.auth.service;

public interface NotificationService {
    void sendNotification(String pushToken, String title, String body, String type, String payload);

    void sendLoginAlert(String pushToken, String deviceName, String location);
}
