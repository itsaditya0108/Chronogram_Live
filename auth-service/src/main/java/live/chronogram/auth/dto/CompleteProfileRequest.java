package live.chronogram.auth.dto;

/**
 * Data Transfer Object for the final step of registration: completing the user
 * profile.
 * Requires a valid registration token obtained from email verification.
 */
public class CompleteProfileRequest {
    /**
     * Mobile number provided during the first step of registration.
     * Logic: Must match the number embedded in the RegistrationToken.
     */
    private String mobileNumber;

    /**
     * User's full legal name.
     * Logic: Validated for alphabetic characters and spaces only.
     */
    private String name;

    /**
     * Birth date (YYYY-MM-DD).
     * Logic: Stored to calculate age groups and for identity verification.
     */
    private String dob;

    /**
     * Cryptographic proof of verification.
     * Logic: This token is issued after successful email OTP verification. 
     * It contains the verified mobile and email, ensuring no data was tampered with between steps.
     */
    private String registrationToken;

    private String country;
    private String city;
    private Double latitude;
    private Double longitude;

    // Device identity to be associated with this new account profile
    private String deviceId;
    private String deviceName;
    private String deviceModel;
    private String osName;
    private String osVersion;
    private String appVersion;
    private String simSerial;
    private String pushToken;

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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
}
