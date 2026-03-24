# Chronogram Auth Service - API Documentation for Flutter Team

**Base URL**: `http://<server-ip>:8086/api/auth` (Port 8086)

## 1. Authentication Flow

### A. Send Mobile OTP (Login/Register)
Call this to initiate login or registration.
**Endpoint**: `POST /send-otp`
**Body**:
```json
{
  "mobileNumber": "9876543210"
}
```

### B. Verify OTP & Login
Call this after user enters OTP.
**Endpoint**: `POST /verify-otp`
**Body**:
```json
{
  "mobileNumber": "9876543210",
  "otpCode": "123456",
  "deviceId": "unique-device-id-from-flutter",
  "simSerial": "8991234...", // REQUIRED for first login validation
  "pushToken": "fcm_token_...", // For Push Notifications
  "deviceName": "My Phone",
  "deviceModel": "iPhone 13",
  "osName": "iOS",
  "osVersion": "17.0",
  "appVersion": "1.0.0",
  "latitude": 28.7041,
  "longitude": 77.1025,
  "isRecoveryFlow": false
}
```
**Response (Success)**:
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "message": "Mobile verified. Verify Email to proceed."
}
```
**Response (Error - New Device Approval Needed)**:
Status: `400 Bad Request` 
Body: `Login failed: APPROVAL_REQUIRED: Please approve login on your trusted device OR use Recovery Flow.`
*Action*: Show screen to user: "Approval sent to your trusted device. Or click 'Recover Account' to use Mobile+Email OTP."

### C. Recover Account (Lost Device)
1. Call `POST /send-otp` (Mobile OTP) with `{"mobileNumber": "..."}`
2. Call `POST /send-email-otp` (Email OTP) with `{"mobileNumber": "..."}`
3. Call `POST /verify-otp` with **both** codes:
```json
{
  "mobileNumber": "...",
  "otpCode": "123456", // Mobile OTP
  "emailOtpCode": "654321", // Email OTP
  "isRecoveryFlow": true, // IMPORTANT flag
  // ... device details
}
```

## 2. Token Management

### A. Refresh Token
Call this silently when `accessToken` expires (401 Unauthorized).
**Endpoint**: `POST /refresh-token?refreshToken=<token>`
**Response**:
```json
{
  "accessToken": "new_access_token...",
  "refreshToken": "same_or_new_refresh_token...",
  "message": "Token refreshed successfully"
}
```

### B. Logout
Call this to clear session on server.
**Endpoint**: `POST /logout?refreshToken=<token>`

## 3. Push Notifications (FCM)
The backend sends the following custom data payloads:

**Type**: `LOGIN_APPROVAL_REQUEST`
- Sent to: **Trusted Devices** when a New Device tries to login.
- Payload: `{"targetDeviceId": "..."}`
- Action: Show prompt "Allow login from [Device Name]?" (App needs to implement Approve API - *Coming Soon*)

**Type**: `LOGIN_ALERT`
- Sent to: **All Devices** when a login succeeds.
- Action: Show notification "New login detected on [Device Name]".

## 4. Profile Management

### A. Get Profile
**Endpoint**: `GET /api/profile`  
**Description**: Fetches the authenticated user's profile information.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`

**Response (Success)**:
```json
{
  "name": "John Doe",
  "photoUrl": "https://cdn.chronogram.live/profiles/1/profile.jpg",
  "email": "john@example.com"
}
```

### B. Update Profile
**Endpoint**: `PUT /api/profile`  
**Description**: Updates the user's name.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`

**Body**:
```json
{
  "name": "Jane Doe"
}
```
**Response (Success)**:
```json
{
  "name": "Jane Doe",
  "photoUrl": "https://...",
  "email": "john@example.com"
}
```

### C. Upload Profile Photo
**Endpoint**: `POST /api/profile/photo`  
**Description**: Uploads a new profile picture.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`  
**Content-Type**: `multipart/form-data`  
**Body**: 
- Key: `file` (type: File)

**Response (Success)**:
```json
{
  "photoUrl": "https://cdn.chronogram.live/profiles/1/profile.jpg"
}
```

## 5. Storage (Images & Videos)

### A. Get Storage Usage
**Endpoint**: `GET /api/storage/usage`  
**Description**: Returns total storage used by images and videos vs the quota limit.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`

**Response (Success)**:
```json
{
  "used": 6.8,
  "limit": 10.0,
  "unit": "GB",
  "warning": false
}
```
*(Note: `warning` will be `true` if `used` >= 9.0 GB)*

### B. Get Storage Details
**Endpoint**: `GET /api/storage/details`  
**Description**: Returns a breakdown of storage used specifically by images and videos.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`

**Response (Success)**:
```json
{
  "photos": 3.2,
  "videos": 2.8,
  "unit": "GB"
}
```

## 6. Settings

### A. Get Sync Preference
**Endpoint**: `GET /api/settings/sync`  
**Description**: Retrieves the user's media sync network preference.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`

**Response (Success)**:
```json
{
  "mode": "WIFI_ONLY"
}
```
*(Modes: `WIFI_ONLY`, `ANY_NETWORK`)*

### B. Update Sync Preference
**Endpoint**: `PUT /api/settings/sync`  
**Description**: Updates the user's media sync network preference.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`

**Body**:
```json
{
  "mode": "ANY_NETWORK"
}
```
**Response (Success)**:
```json
{
  "message": "Sync preference updated"
}
```

## 7. Account & App Info

### A. Delete Account
**Endpoint**: `DELETE /api/account`  
**Description**: Triggers account deletion (soft delete) and invalidates active sessions. Required for Apple Submissions.  
**Header**: `Authorization: Bearer <ACCESS_TOKEN>`

**Response (Success)**:
```json
{
  "message": "Account deleted"
}
```

### B. App Info (Public)
**Endpoint**: `GET /api/app/info`  
**Description**: Fetches the latest app version and build info. Can be called without auth.

**Response (Success)**:
```json
{
  "version": "1.0.3",
  "build": 25,
  "minSupported": "1.0.0"
}
```

### C. Privacy Policy (Public)
**Endpoint**: `GET /api/app/privacy`  
**Description**: Returns the URL to the app's privacy policy.

**Response (Success)**:
```json
{
  "url": "https://chronogram.live/privacy"
}
```
