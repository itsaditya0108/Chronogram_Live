# 📸 Encrypted Image Sync Service - Testing Guide

This guide covers how to test the new Encrypted Image Background Sync functionality using Postman. Because this service uses chunked uploads and background pool workers, the flow is different from a standard direct upload.

---

## 🚀 Postman Testing Flow

### 1. Initialize Sync Session (Optional)
Used strictly to enforce the `24-hour limit` for automatic background syncs.

*   **Endpoint:** `POST /api/sync/init`
*   **Headers:**
    *   `Authorization`: `Bearer <your_jwt_token>`
*   **Params:**
    *   `triggerType`: `MANUAL` or `AUTO_WIFI`
*   **Response:**
    *   Returns a `SyncSession` object. Note the `id` from this response (this is your `syncSessionId`).
    *   *Test:* If you send `AUTO_WIFI` twice within 24 hours, the server will correctly return a **429 Too Many Requests** error.

---

### 2. Initialize Chunked Upload
**Why do we need this step?** Because mobile apps (Flutter) often drop connection on large photos/videos, we don't upload the file immediately. First, we tell the server *"Hey, I'm about to send a file of X size broken into Y pieces."* The server uses this to check if you have enough quota and gives you an `uploadId` to track the pieces.

*   **Endpoint:** `POST /api/sync/upload/init`
*   **Headers:**
    *   `Authorization`: `Bearer <your_jwt_token>`
*   **Params:**
    *   `originalFilename`: `test_image.jpg`
    *   `totalChunks`: `1` *(For simple Postman testing, we will just send the whole file as 1 chunk. Set to >1 if you manually split a file).*
    *   `totalFileSize`: `102400` (Approximate File size in bytes)
    *   `syncSessionId`: `<id_from_step_1>` (Optional)
*   **Response:**
    *   Returns an `UploadSession` object.
    *   **CRITICAL:** Copy the `uploadId` (a UUID string like `abc-123-def`) for the next step.

---

### 3. Upload File Chunks
Upload the actual pieces of the file sequentially or in parallel.

*   **Endpoint:** `POST /api/sync/upload/{uploadId}/chunk` (Replace `{uploadId}` with the UUID from Step 2)
*   **Method:** `POST`
*   **Headers:**
    *   `Authorization`: `Bearer <your_jwt_token>`
*   **Body (form-data):**
    *   `chunk`: [Attach the full `test_image.jpg` file here]
    *   `chunkIndex`: `0` (Since `totalChunks` is 1, index 0 is the final chunk)
*   **Response:**
    *   Once you hit the final chunk (`chunkIndex: 1`), the response will say: `"message": "All chunks received. Merging and encrypting in background."`

---

### 4. Check Upload Status (Async Worker Pool)
Because hashing, deduplication, and AES-256 encryption happen asynchronously in the `MergeWorkerPool`, you pull the status to see if it finished.

*   **Endpoint:** `GET /api/sync/upload/{uploadId}/status`
*   **Headers:**
    *   `Authorization`: `Bearer <your_jwt_token>`
*   **Response:**
    *   Watch the `status` field change from `MERGING` to `COMPLETED` (or `FAILED` if the file was corrupt/missing chunks).

---

## 🛡️ How to Test Duplicate Image Uploads (Deduplication)

We built a strict hashing system to ensure that if a user uploads the exactly identical image twice, we don't save a redundant copy on the hard drive. Here is how to test it step-by-step in Postman:

### Step 1: Upload a fresh image (First Time)
1. Run **Step 2 (Initialize Chunked Upload)** to get an `uploadId`. Let's say it is `upload-111`.
2. Run **Step 3 (Upload File Chunks)** for `upload-111`, attaching a picture (e.g. `car.jpg`).
3. Run **Step 4 (Check Upload Status)** until it says `COMPLETED`.
4. **Result:** The system hashed `car.jpg`, didn't find it in the DB, encrypted it, and saved an `.enc` file to the hard drive. 

### Step 2: Upload the EXACT SAME image again (The Duplicate Test)
1. Run **Step 2 (Initialize Chunked Upload)** to get a **NEW** `uploadId`. Let's say it is `upload-222`.
2. Run **Step 3 (Upload File Chunks)** for `upload-222`, attaching the **EXACT SAME** `car.jpg` file.
3. Run **Step 4 (Check Upload Status)** until it says `COMPLETED`.

### Step 3: Verify the Deduplication Worked
1. **Check Database (`images` table):** 
   * You will see **TWO** rows in the database (one for `upload-111` and one for `upload-222`). This is perfectly correct because the user believes they have two images in their gallery.
   * **Crucial Detail:** Look at the `storage_path` column for both rows. They will be pointing to the **EXACT SAME** `.enc` file path! 
2. **Check Hard Drive (`storage/users/...`):**
   * Even though the user uploaded it twice, there is only **ONE** encrypted `.enc` file on the disk. The background worker detected the duplicate `contentHash` and gracefully skipped the encryption/save phase!

---

## 🗓️ Developer Cron Job Note
A Spring `@Scheduled` thread automatically scans for temporary files (chunks) from `UploadSession`s that sit idle for longer than 24 hours (e.g., the Flutter app lost internet connection and never sent the final chunk). The backend automatically clears these out to prevent disk fatigue.

---

## 📷 Standard Image API Testing

If you are not using the background chunked sync, you can test the standard image endpoints via Postman as well.

### 1. Upload Standard Images (Bulk)
*   **Endpoint:** `POST /api/images/bulk`
*   **Headers:** `Authorization: Bearer <your_jwt_token>`
*   **Body (form-data):**
    *   `files`: [Select multiple image files]
*   **Response:** Array of `Image` metadata objects.

### 2. Get User Images
*   **Endpoint:** `GET /api/images?type=all&size=20`
*   **Headers:** `Authorization: Bearer <your_jwt_token>`
*   **Response:** Paginated list of user images.

### 3. View/Download Decrypted Image
*   **Endpoint (Thumbnail):** `GET /api/images/{id}/thumbnail`
*   **Endpoint (Full Size):** `GET /api/images/{id}/download`
*   **Headers:** `Authorization: Bearer <your_jwt_token>` (Or pass `?token=<your_jwt_token>` in the URL)
*   **Response:** The raw image binary stream (it decrypts the AES-256 file dynamically and streams the bytes out).

---

## 👤 Profile Picture Testing

Profile pictures use a standard upload and are unencrypted, separate from the `ImageService` vault.

### 1. Upload Profile Picture
*   **Endpoint:** `POST /api/profile-picture`
*   **Headers:** `Authorization: Bearer <your_jwt_token>`
*   **Body (form-data):**
    *   `file`: [Select image file]

### 2. View Profile Picture
*   **Endpoint (Small/Medium):** `GET /api/profile-picture/{id}/view/small` (or `medium`)
*   **Params:** `?token=<your_jwt_token>`
*   **Response:** The unencrypted image file inline.

### 3. Get History
*   **Endpoint:** `GET /api/profile-picture/history`
*   **Headers:** `Authorization: Bearer <your_jwt_token>`
*   **Response:** Array of history details (which picture is active, etc.).
