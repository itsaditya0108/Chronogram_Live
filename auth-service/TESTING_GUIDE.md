# 🧪 Auth Service API Guide (Postman)

**Base URL:** `http://localhost:8086/api` (Postman/Emulator)
**Base URL:** `http://<YOUR_LAN_IP>:8086/api` (Physical Device, e.g., `192.168.1.4`)
**Base URL (Ngrok):** `https://<YOUR_NGROK_ID>.ngrok-free.dev/api` (Universal/Flutter Physical Device)

> ### 🚀 Recommended: Using Ngrok for Flutter Testing (Bypasses Firewall & LAN Issues)
> If you are testing on a real iOS/Android device from Flutter, the absolute easiest method is to tunnel your local port to the internet using **ngrok**.
> 
> 1. **Ensure your API is running:** `auth-service` typically runs on port 8086.
> 2. **Run ngrok:** Open a terminal and run `ngrok http 8086` (or `ngrok http 80` if you're using an API Gateway like Nginx).
> 3. **Copy the Forwarding URL:** E.g., `https://glayds-unpainful-torri.ngrok-free.dev`
> 4. **Update Flutter & Postman:** Change your base URL to `https://glayds-unpainful-torri.ngrok-free.dev/api`. Note: Drop the `http://localhost:8086` part entirely.
> 5. *Why?* This immediately bypasses all Windows Firewall restrictions, meaning you don't even have to be on the same Wi-Fi network!

> ### 🍎 Alternative: Mac Flutter Dev -> Windows Server Connection Guide (Local Network)
> If not using ngrok and the Backend is running on a Windows PC and the Flutter App is running on a Mac/Phone, `localhost:8086` **will not work**. 
> 
> **How to fix "Connection Refused / Timeout":**
> 1.  **Find the Windows PC's Local IP:** Open `cmd` on Windows, type `ipconfig`, and find the `IPv4 Address` (e.g., `192.168.1.100`).
> 2.  **Both on same Wi-Fi:** Ensure devices are connected to the EXACT same network.
> 3.  **Update Flutter Base URL:** In your Flutter code, change the API base URL to `http://<WINDOWS_IPV4_ADDRESS>:8086` (e.g., `http://192.168.1.100:8086/api`).
> 4.  **Windows Firewall (Crucial):** If it still times out, on the Windows PC:
>     *   Go to `Inbound Rules` -> `New Rule...` -> `Port` -> `TCP`, Specific local ports: `8086` -> `Allow the connection` -> Name it `Spring Boot Auth 8086`.

**Note:** All API requests should send the number as entered by the user. The backend handles the sanitization.

---

## 🛡️ Security & Validations
The system now includes strict security and profile validations:
*   **Age Restriction:** Users must be **12 years or older** to register. Providing a Date of Birth under 12 during the `/api/auth/complete-profile` step will return a `400 Bad Request`.
*   **Email Length Constraints:** The local-part of an email (the text before `@`) is strictly limited to a maximum of **64 characters**. Total email length cannot exceed 254 characters (`400 Bad Request`).
*   **OTP Rate Limiting & Lockout:** 
    *   Entering an invalid OTP **5 times** will trigger an automatic **30-minute account block**.
    *   During this 30-minute window, the user cannot generate new OTPs and cannot verify existing ones.
    *   The API returns HTTP `429 Too Many Requests` when limits are exceeded, specifying the remaining wait time.

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

## 📱 Device ID Requirement (Crucial)
**ALL** requests involving login, registration, or OTP verification **MUST** include a `deviceId`.
*   If `deviceId` is missing, the API will return `400 Bad Request` with message: `Device ID is required.`
*   Generate a unique UUID for `deviceId` on the client side and persist it.

---


## 1️⃣ Registration Flow (New User - Stateless)

### Step 1: Send Mobile OTP
**POST** `/api/auth/register/send-otp`
```json
{
    "mobileNumber": "9876543210",
    "deviceId": "DEVICE_ID_1"
}
```
**Response:** `200 OK` ("OTP sent successfully.")
**Errors:** 
*   `409 Conflict` ("User already registered. Please login.") if the number is already registered.

### Step 2: Verify Mobile OTP
**POST** `/api/auth/verify-otp`
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
**POST** `/api/auth/send-email-otp`
```json
{
    "email": "user@example.com",
    "registrationToken": "<PASTE_REGISTRATION_TOKEN_STEP_1>"
}
```
**Response:** `200 OK` ("Email OTP sent successfully...")

### Step 4: Verify Email OTP
**POST** `/api/auth/verify-email-registration-otp`
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
**POST** `/api/auth/complete-profile`
```json
{
    "name": "John Doe",
    "dob": "1990-01-01",
    "mobileNumber" : "9876543210",
    "registrationToken": "<PASTE_REGISTRATION_TOKEN_STEP_2>",
    "deviceId": "DEVICE_ID_1",
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
*   **Body:** `{"accessToken": "eyJ...", "refreshToken": "SAMPLE_REFRESH_TOKEN", "message": "Registration complete. Welcome!"}`
*   **Success:** User is now created in the database and logged in.

---

## 2️⃣ Login Flow (Existing User, Trusted Device)

### Step 1: Send Login OTP
**POST** `/api/auth/login/send-otp`
```json
{
    "mobileNumber": "9876543210",
    "deviceId": "DEVICE_ID_1"
}
```
**Response:** `200 OK` ("OTP sent successfully.")
**Errors:** 
*   `404 Not Found` ("User not found. Please register.") if the user does not exist.

### Step 2: Verify Login (Existing User)
**POST** `/api/auth/verify-login-otp`
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
**POST** `/api/auth/login/send-otp`
```json
{
    "mobileNumber": "9876543210",
    "deviceId": "DEVICE_ID_NEW_99"
}
```

### Step 2: Verify OTP (Expect Approval Request)
**POST** `/api/auth/verify-login-otp`
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
    "maskedEmail": "us***@example.com",
    "temporaryToken": "eyJhb..."
}
```
*Meaning: Device not trusted. Email OTP has been sent automatically. The `temporaryToken` is required for any OTP resends.*

### Step 3: Verify New Device (Email OTP)
**POST** `/api/auth/verify-new-device`
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

### Step 4 (Optional): Resend Email OTP for New Device
If the user didn't receive the email OTP for new device verification, you can resend it by calling:
**POST** `/api/auth/resend-new-device-otp`
```json
{
    "temporaryToken": "<PASTE_TEMPORARY_TOKEN_FROM_401_RESPONSE>"
}
```
**Response:** `200 OK` ("New Device OTP resent successfully to registered email.")

## 🔄 Resend OTP API

This endpoint allows you to resend an OTP for either mobile number or email address, depending on the payload.

### Resend Registration Mobile OTP
**POST** `/api/auth/register/resend-otp`
```json
{
    "mobileNumber": "9876543210"
}
```
**Response:** `200 OK` ("Mobile OTP resent successfully.")

### Resend Login Mobile OTP
**POST** `/api/auth/login/resend-otp`
```json
{
    "mobileNumber": "9876543210"
}
```
**Response:** `200 OK` ("Mobile OTP resent successfully.")

### Resend Registration Email OTP
**POST** `/api/auth/register/resend-otp`
```json
{
    "email": "user@example.com",
    "registrationToken": "<OPTIONAL_REGISTRATION_TOKEN_IF_IN_REGISTRATION_FLOW>"
}
```
**Response:** `200 OK` ("Email OTP resent successfully.")

---

## 4️⃣ User Details API (Secure)

### Get Current User Profile
**GET** `/api/auth/me`
*   **Headers:**
    *   `Authorization`: `Bearer <YOUR_ACCESS_TOKEN>`

**Response:** `200 OK`
```json
{
    "userId": 123,
    "name": "John Doe",
    "email": "user@example.com",
    "mobileNumber": "+919876543210",
    "dob": "1990-01-01",
    "profilePictureUrl": "https://api.dicebear.com/...",
    "mobileVerified": true,
    "emailVerified": true,
    "status": "Active"
}
```

**Note:** This endpoint requires a valid JWT token in the header.

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
