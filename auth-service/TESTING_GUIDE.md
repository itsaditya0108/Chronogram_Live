# 🧪 Auth Service API Guide (Postman)

**Base URL:** `http://localhost:8086/api` (Postman/Emulator)
**Base URL:** `http://<YOUR_LAN_IP>:8086/api` (Physical Device, e.g., `192.168.1.4`)

> **Note for Mobile Testing:**
> If testing on a physical device, ensure your phone and PC are on the same Wi-Fi.
> You MUST use your PC's local IP address (e.g., `192.168.1.x`) instead of `localhost`.
> Ensure your firewall allows traffic on port `8086`.

---

## 📞 Phone Number Formatting
The system now **automatically formats** mobile numbers to ensure consistency:
*   **Input:** `9876543210` (10 digits) -> **Saved:** `+919876543210`
*   **Input:** `+919876543210` -> **Saved:** `+919876543210`
*   **Input:** `919876543210` -> **Saved:** `+919876543210`
*   **Input:** `98765 43210` (spaces/dashes) -> **Saved:** `+919876543210`

**Note:** All API requests should send the number as entered by the user. The backend handles the sanitization.

---

## 🌍 IP Address Capture
The system now automatically captures the client's IP address during:
*   Registration (`/verify-otp`)
*   Login (`/verify-login-otp`)
*   New Device Verification (`/verify-new-device`)
*   Profile Completion (`/complete-profile`)

This serves as a fallback for location data if latitude/longitude are not provided.
*   **Postman/Localhost:** IP will likely be `127.0.0.1` or `0:0:0:0:0:0:0:1`.
*   **Production/LAN:** Real client IP will be captured (handling `X-Forwarded-For` headers).

---

## 1️⃣ Registration Flow (New User - Stateless)

### Step 1: Send Mobile OTP
**POST** `/auth/send-otp`
```json
{
    "mobileNumber": "9876543210"
}
```

### Step 2: Verify Mobile OTP
**POST** `/auth/verify-otp`
*Note: `simSerial` is required for Postman testing to pass validation.*
```json
{
    "mobileNumber": "9876543210",
    "otpCode": "000000",
    "deviceId": "DEVICE_ID_1",
    "simSerial": "SIM_SERIAL_123",
    "deviceName": "Postman",
    "deviceModel": "Virtual",
    "osName": "Windows",
    "osVersion": "10",
    "appVersion": "1.0",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "country": "India",
    "city": "New Delhi"
}
```
**Response:** `200 OK`
*   **Body:** `{"accessToken": "eyJ...", "message": "Mobile verified. Verify Email to proceed."}`
*   **Action:** Copy the `accessToken`. This is your **Registration Token (Step 1)**.

### Step 3: Send Email OTP
**POST** `/auth/send-email-otp`
```json
{
    "email": "user@example.com",
    "registrationToken": "<PASTE_REGISTRATION_TOKEN_STEP_1>"
}
```
**Response:** `200 OK` ("Email OTP sent successfully...")

### Step 4: Verify Email OTP
**POST** `/auth/verify-email-registration-otp`
```json
{
    "email": "user@example.com",
    "otpCode": "000000",
    "registrationToken": "<PASTE_REGISTRATION_TOKEN_STEP_1>"
}
```
**Response:** `200 OK`
*   **Body:** `{"accessToken": "eyJ...", "message": "Email verified. Complete profile to finalize registration."}`
*   **Action:** Copy the `accessToken`. This is your **Registration Token (Step 2)**.

### Step 5: Complete Profile & Create User
**POST** `/auth/complete-profile`
```json
{
    "name": "John Doe",
    "dob": "1990-01-01",
    "registrationToken": "<PASTE_REGISTRATION_TOKEN_STEP_2>",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "country": "India",
    "city": "New Delhi"
}
```
**Response:** `200 OK`
*   **Body:** `{"accessToken": "eyJ...", "refreshToken": "SAMPLE_REFRESH_TOKEN", "message": "Registration complete. Welcome!"}`
*   **Success:** User is now created in the database and logged in.

---

## 2️⃣ Login Flow (Existing User, Trusted Device)

### Step 1: Send Login OTP
**POST** `/auth/send-otp`
```json
{
    "mobileNumber": "9876543210"
}
```

### Step 2: Verify Login (Existing User)
**POST** `/auth/verify-login-otp`
```json
{
    "mobileNumber": "9876543210",
    "otpCode": "123456",
    "deviceId": "DEVICE_ID_1",
    "simSerial": "SIM_SERIAL_123",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "country": "India",
    "city": "New Delhi"
}
```
**Response:** `200 OK` + `accessToken`

---

## 3️⃣ New Device Login Flow (Untrusted Device)

### Step 1: Send OTP (on New Device)
**POST** `/auth/send-otp`
```json
{
    "mobileNumber": "9876543210"
}
```

### Step 2: Verify OTP (Expect Approval Request)
**POST** `/auth/verify-login-otp`
```json
{
    "mobileNumber": "9876543210",
    "otpCode": "123456",
    "deviceId": "DEVICE_ID_NEW_99",
    "simSerial": "SIM_SERIAL_999",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "country": "India",
    "city": "New Delhi"
}
```
**Response:** `401 Unauthorized`
```json
{
    "status": 401,
    "error": "Unauthorized",
    "message": "APPROVAL_REQUIRED...",
    "maskedEmail": "us***@example.com"
}
```
*Meaning: Device not trusted. Email OTP has been sent automatically.*

### Step 3: Verify New Device (Email OTP)
**POST** `/auth/verify-new-device`
```json
{
    "mobileNumber": "9876543210",
    "otp": "654321", 
    "deviceId": "DEVICE_ID_NEW_99",
    "deviceName": "New Device",
    "deviceModel": "Model X",
    "osName": "Android",
    "osVersion": "12",
    "appVersion": "1.0",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "country": "India",
    "city": "New Delhi"
}
```
**Response:** `200 OK`
*   **Body:** `{"accessToken": "eyJ...", "message": "New device verified and logged in."}`
*   **Success:** Device is now trusted and user is logged in.

---

## ❌ Common Errors & Troubleshooting

| Error Code | Message | Cause | Solution |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `Mobile number is required` | Missing `mobileNumber` in JSON body. | Ensure `mobileNumber` key is present. |
| `400 Bad Request` | `Invalid Mobile OTP` | The `otpCode` provided does not match the one generated. | Use the correct OTP from console logs. |
| `400 Bad Request` | `User already exists. Please login.` | Trying to register a mobile number that is already in use. | Use `/auth/send-otp` and `/auth/verify-login-otp` instead. |
| `400 Bad Request` | `User not found. Please register.` | Trying to login with a number that isn't registered. | Use `/auth/send-otp` and `/auth/verify-otp` (Registration flow). |
| `401 Unauthorized` | `APPROVAL_REQUIRED...` | Logging in from a new, untrusted device. | Check email for OTP and use `/auth/verify-new-device`. |
| `400 Bad Request` | `Email already in use` | Trying to link an email that is already associated with another user. | Use a different email address. |

---
