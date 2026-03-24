package live.chronogram.auth.dto;

/**
 * Data Transfer Object for authentication and login requests.
 * Supports both standard OTP login and recovery flows for lost devices.
 */
public class LoginRequest {
    /**
     * User's mobile number. Must be unique in the system.
     * Logic: Standardized via sanitizePhoneNumber() before lookup.
     */
    private String mobileNumber;

    /**
     * Mobile OTP code. 
     * Logic: Verified against OtpVerification table with 5-attempt lockout.
     */
    private String otpCode;

    /**
     * Session anchor.
     * Logic: Ensures the 'verify' call is coming from the same device/intent that triggered 'send-otp'.
     */
    private String otpSessionToken;

    /**
     * Email OTP code.
     * Logic: Required only if the device is new/untrusted or in recovery mode.
     */
    private String emailOtpCode;

    /**
     * Physical device identifier (e.g., Android ID or iOS UUID).
     * Logic: Used to determine if the device is 'Trusted' or needs secondary email approval.
     */
    private String deviceId;

    /**
     * SIM Hardware ID.
     * Logic: Provides an extra layer of binding to prevent account takeover via remote emulators.
     */
    private String simSerial;

    /**
     * Cloud messaging token.
     * Logic: Stored in UserDevice to enable push notifications (FCM/APNS).
     */
    private String pushToken;

    private String deviceName;  // e.g., "Aditya's iPhone"
    private String deviceModel; // e.g., "iPhone 13 Pro"
    private String osName;      // e.g., "iOS"
    private String osVersion;   // e.g., "16.4"
    private String appVersion;  // e.g., "1.0.5"
    
    // Geolocation data for audit logging and security alerts
    private Double latitude;
    private Double longitude;

    /**
     * Recovery trigger.
     * Logic: If true, the system bypasses 'Trusted Device' checks and forces Email OTP verification.
     */
    private boolean isRecoveryFlow;

    private String country;
    private String city;

    /**
     * Firebase ID Token for external authentication verification.
     */
    private String firebaseIdToken;

    // Getters and Setters
    public String getFirebaseIdToken() {
        return firebaseIdToken;
    }

    public void setFirebaseIdToken(String firebaseIdToken) {
        this.firebaseIdToken = firebaseIdToken;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public String getOtpSessionToken() {
        return otpSessionToken;
    }

    public void setOtpSessionToken(String otpSessionToken) {
        this.otpSessionToken = otpSessionToken;
    }

    public String getEmailOtpCode() {
        return emailOtpCode;
    }

    public void setEmailOtpCode(String emailOtpCode) {
        this.emailOtpCode = emailOtpCode;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSimSerial() {
        return simSerial;
    }

    public void setSimSerial(String simSerial) {
        this.simSerial = simSerial;
    }

    public String getPushToken() {
        return pushToken;
    }

    public void setPushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public boolean isRecoveryFlow() {
        return isRecoveryFlow;
    }

    public void setRecoveryFlow(boolean recoveryFlow) {
        isRecoveryFlow = recoveryFlow;
    }
}
