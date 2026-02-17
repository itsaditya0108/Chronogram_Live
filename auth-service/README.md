# Auth Service (Authentication Microservice)

This service provides secure, passwordless mobile authentication for the Chronogram platform.

## Features
- **Mobile & Email OTP Login**
- **Device Trust & Binding** (SIM Serial checking)
- **Push Notifications (FCM)**: Login Alerts & Verification
- **JWT Session Management** (Persistent 1-Year Sessions for Mobile)
- **Security Audit Logging**

## Prerequisites
- MySQL Database running on `localhost:3306`
- Database `auth_service_db` created with `auth_service_schema.sql`

## How to Run
1.  Navigate to `d:\Capri_Nexo\auth-service`
2.  Run with Maven:
    ```bash
    ./mvnw spring-boot:run
    ```

## API Endpoints

### 1. Send OTP
**POST** `/api/auth/send-otp`
```json
?mobileNumber=9876543210
```

### 2. Verify OTP & Login
**POST** `/api/auth/verify-otp`
Accepts `pushToken` for FCM integration.
```json
{
  "mobileNumber": "9876543210",
  "otpCode": "123456",
  "dvcId": "unique-device-id-123", 
  "simSerial": "89912345...",
  "pushToken": "fcm_token_xyz...",
  "deviceName": "My Phone",
  "deviceModel": "iPhone 13",
  "osName": "iOS",
  "osVersion": "17.0",
  "appVersion": "1.0.0",
  "latitude": 28.7041,
  "longitude": 77.1025
}
```

### 3. Recovery Login
To recover a lost device, set `isRecoveryFlow: true`, provide `emailOtpCode`, and call `/api/auth/send-email-otp` first.

## Push Notifications (FCM)
- A mock `FcmNotificationService` is provided.
- To enable real notifications, inject `FirebaseMessaging` in `FcmNotificationService.java`.
- **Logic**: When a new device tries to login, an alert (mock) is sent to all *other* trusted devices.

## Security Notes
- **JWT Secret**: Configured in `application.properties`.
- **Device Trust**: Login on a new device triggers "Approval Required" unless it is the *first* device or *recovery flow* is used.
