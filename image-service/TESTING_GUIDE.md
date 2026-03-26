# 📸 Image Service - Complete Testing Guide

This guide is divided into two distinct parts. Please share only the relevant part with the corresponding developer team.

1.  **📱 PART 1: MOBILE APP (Flutter / APK)** - Core image features for end-users.
2.  **💻 PART 2: ADMIN PANEL (Website / Management)** - Advanced sync and system monitoring.

---

# 📱 PART 1: MOBILE APP (Flutter / APK)
*Target: Flutter Developers & Mobile QA*

### ⚡ Quick Verification Flow (for Testers)
1.  **Auth**: Get `accessToken` from `auth-service` (Phase 1).
2.  **Upload**: Post files to `POST /api/images/bulk` (Phase 2, Step 1).
3.  **Feed**: Confirm files appear in `GET /api/images` (Phase 2, Step 2).
4.  **Profile**: Upload via `POST /api/profile-picture` and view it at `/medium`.

---

## 🔐 PHASE 1: Authentication (Getting the Token)
The Image Service requires a Bearer JWT token from the **Auth Service**.

### Step 1: Send OTP to Mobile Number
*   **Endpoint:** `POST http://localhost:8086/api/auth/register/send-otp`
*   **Body (JSON):**
    ```json
    { "mobileNumber": "9876543210", "deviceId": "TEST_DEVICE_ID" }
    ```
*   **Response:** `{"test_otp": "123456", "otpSessionToken": "..."}`

### Step 2: Verify OTP & Get Token
*   **Endpoint:** `POST http://localhost:8086/api/auth/verify-otp` (Register)
*   **Response:**
    ```json
    {
      "accessToken": "eyJhbGci...",
      "refreshToken": "..."
    }
    ```
*   **CRITICAL**: Copy the `"accessToken"` and use it as `Authorization: Bearer <token>` in all requests below.

---

## 🖼️ PHASE 2: Image Vault (Personal & Chat)
Securely upload and view encrypted images.

> [!NOTE]
> All image uploads are encrypted at rest. Supported formats are validated against 20 approved extensions (`.jpg`, `.png`, `.webp`, `.heic`, `.avif`, `.gif`, `.bmp`, `.tif`, `.tiff`, `.ico`, `.svg`, `.svgz`, `.heif`, `.apng`, `.dib`, `.cur`, `.jpe`, `.jfif`, `.pjpeg`). Unsupported files are silently skipped.

### 1. Upload Images (Bulk) ⚡ [High-Performance]
*   **Endpoint:** `POST http://localhost:8084/api/images/bulk`
*   **Auth:** `Authorization: Bearer <accessToken>`
*   **⚡ Optimization**: The backend now uses parallel streams to process resizing, encryption, and storage simultaneously, minimizing total upload time.
*   **⚡ Optimization**: High-speed parallel processing enabled.
*   **📏 Limits**: 
    - **Max Size**: 15MB per image.
    - **Max Count**: 1000 images per batch request.
    - **Global Quota**: 10GB total (Shared with Video Service).
*   **Error Case**: If the 10GB limit is exceeded, you will receive:
    - **Status**: `400 Bad Request`
    - **Error Code**: `STORAGE_QUOTA_EXCEEDED`
*   **Error Case (No Selection)**: If the `files` list is empty or parts are empty:
    - **Status**: `400 Bad Request`
    - **Error Code**: `REQUEST_FAILED`
    - **Message**: `NO_IMAGES_SELECTED`
*   **Error Case (Security)**: If malicious files (.exe, .bat, etc.) are detected:
    - **Status**: `400 Bad Request`
    - **Error Code**: `REQUEST_FAILED`
    - **Message**: `SECURITY_THREAT: Malicious file format...`
*   **Error Case (Missing Part)**: If the `files` field is missing entirely:
    - **Status**: `400 Bad Request`
    - **Error Code**: `MISSING_PART`
*   **Body (Multipart Form-Data):**
    - `files`: One or more image files.
    - `type`: `personal` (default) or `chat`.
*   **Response `200 OK` (Unified):**
    ```json
    {
      "totalFiles": 1,
      "syncedCount": 1,
      "skippedCount": 0,
      "alreadyUploadedCount": 0,
      "images": [
        {
          "id": 101,
          "originalFilename": "vacation.jpg",
          "contentType": "image/jpeg",
          "fileSize": 450000,
          "createdAt": "2026-03-19T10:00:00",
          "imageUrl": "/api/images/101/download",
          "thumbnailUrl": "/api/images/101/thumbnail",
          "status": "UPLOADED" 
        }
      ],
      "errors": []
    }
    ```
*   **Deduplication**: If an image with the same hash already exists for the user, the API returns the existing record in the `images` list with `status: "ALREADY_EXISTS"`. If it's a new upload, the status will be `"UPLOADED"`.

### 2. Get User's Images (Feed)
*   **Endpoint:** `GET http://localhost:8084/api/images?type=all&page=0&size=20`
*   **Auth:** `Authorization: Bearer <accessToken>`
*   **Query Params:**
    - `type`: `all` (default), `personal`, or `chat`
    - `page`, `size`: Pagination
*   **Response `200 OK`:**
    ```json
    {
      "content": [
        {
          "id": 101,
          "originalFilename": "vacation.jpg",
          "imageUrl": "/api/images/101/download",
          "thumbnailUrl": "/api/images/101/thumbnail",
          "fileSize": 450000,
          "createdAt": "2026-03-19T10:00:00"
        }
      ],
      "totalElements": 1,
      "totalPages": 1,
      "last": true
    }
    ```

### 3. Download Image & Thumbnail
*   **Original Image:** `GET http://localhost:8084/api/images/{id}/download`
*   **Thumbnail:** `GET http://localhost:8084/api/images/{id}/thumbnail`
*   **Decrypted Stream:** `GET http://localhost:8084/api/images/{id}/stream` (or `/stream?thumbnail=true`)
*   **Behavior:** Returns the raw byte stream of the decrypted image. Flutter can render this directly as a NetworkImage.

### 4. Get Variant of an Image (e.g. WebP)
*   **Endpoint:** `GET http://localhost:8084/api/images/{id}/variants/{type}`
*   **Example:** `GET http://localhost:8084/api/images/101/variants/webp`
*   **Auth:** `Authorization: Bearer <accessToken>`
*   **Behavior:** Returns the file stream for the requested format variant.

---

## 👤 PHASE 3: Profile Picture Management
Unencrypted profile icons with history.

### 1. Upload New Profile Picture
*   **Endpoint:** `POST http://localhost:8084/api/profile-picture`
*   **Auth:** `Authorization: Bearer <accessToken>`
*   **Body (form-data):** `file`: [Select Image]
*   **Response `200 OK`:**
    ```json
    {
      "id": 10,
      "userId": 42,
      "originalPath": "profiles/42/original.jpg",
      "smallPath": "/api/profile-picture/42/small",
      "mediumPath": "/api/profile-picture/42/medium",
      "active": true,
      "uploadedAt": "2026-03-19T11:00:00"
    }
    ```

### 2. View Active Profile Picture
*   **Medium (Recommended):** `GET http://localhost:8084/api/profile-picture/medium`
*   **Small (Icon):** `GET http://localhost:8084/api/profile-picture/small`
*   **Other User:** `GET http://localhost:8084/api/profile-picture/{userId}/medium`

### 3. Profile Picture History
*   **Endpoint:** `GET http://localhost:8084/api/profile-picture/history`
*   **Response `200 OK`:**
    ```json
    [
      { "id": 10, "smallPath": "/api/profile-picture/42/small", "active": true },
      { "id": 9, "smallPath": "/api/profile-picture/42/view/small", "active": false }
    ]
    ```

### 4. Set Old Profile Picture as Active
*   **Endpoint:** `PUT http://localhost:8084/api/profile-picture/{id}/active`
*   **Auth:** `Authorization: Bearer <accessToken>`
*   **Behavior:** Deactivates the currently active profile picture and sets the specified profile picture as active.
*   **Response:** `200 OK`

---

# 💻 PART 2: ADMIN PANEL (Website / Management)
*Target: Web Developers & Backend Administrators*

> [!CAUTION]
> **INTERNAL USE ONLY**: The following APIs are for the Chronogram Admin Dashboard and background sync workers. **Do not share this section with mobile/flutter developers.**

## 🔄 PHASE 4: Background Sync & Chunked Upload
Handles heavy-duty gallery syncs from the background.

### Chunked Upload Flow
1.  **Init Upload:** `POST /api/sync/upload/init`
    - **Body**: `{"originalFilename": "...", "totalChunks": 10, "totalFileSize": 52428800, "contentHash": "..."}`
    - **Response (New)**: Returns `status: "INITIATED"`.
    - **Deduplication**: If file exists, returns `status: "ALREADY_EXISTS"` and `existingImageId`.
    - **Resume**: If session active, returns current status (e.g., `UPLOADING`, `MERGING`).
2.  **Upload Chunk:** `POST /api/sync/upload/{uploadId}/chunk` (Headers: `X-Chunk-Index`, Body: RAW BINARY)
3.  **Check Status:** `GET /api/sync/upload/{uploadId}/status`

---

## 📊 PHASE 5: Internal & Storage APIs
Monitoring and health check endpoints.

*   **Global Summary:** `GET http://localhost:8084/internal/storage/summary`
*   **User Storage Usage:** `GET http://localhost:8084/internal/storage/user/{userId}`

### Bulk Fetch (Restore)
To recover the gallery:
1.  **List**: `GET /api/images`
2.  **Download**: Iterate over the list and call `GET /api/images/{id}/download`.

---

## 💾 Restore & Recovery Protocol
To restore a photo gallery on a new device:
1.  **Sync Metadata**: Fetch the list of all images via `GET /api/images`.
2.  **Deduplicate**: Compare `contentHash` with local files to avoid redundant downloads.
3.  **Secure Download**: Download each missing image via `GET /api/images/{id}/download`.
4.  **Vault Security**: All data is streamed via **AES-256 GCM Secure Tunnel**.

---

## 🏗️ Database Schema (Single-Table Policy)
| Table | Description |
|---|---|
| `images` | Main image metadata (path, hash, user, size, etc.) |
| `approved_formats` | Global Format Policy (extensions permitted for upload/sync) |
| `profile_pictures` | Profile picture history per user |
| `sync_sessions` | Background sync session tracking |
| `upload_sessions` | Chunked upload session state |

> [!NOTE]
> Approved image formats are validated against **20 high-compatibility extensions** defined in the `approved_formats` database table. This list is optimized for **Senior-Approved APK compatibility**.

---
**Happy Testing!** 🚀
