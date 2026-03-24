package live.chronogram.auth.dto;

/**
 * Data Transfer Object for requesting a new OTP (One-Time Password).
 * Used for login, registration, and device verification flows.
 */
public class OtpRequest {
    /**
     * Mobile number to which the OTP should be sent.
     */
    private String mobileNumber;

    /**
     * Email address to which the OTP should be sent (e.g., for email verification).
     */
    private String email;

    /**
     * Token used to authorize email OTP sending during the registration flow.
     */
    private String registrationToken;

    /**
     * Temporary token for authorizing OTP resends or specific security steps.
     */
    private String temporaryToken;

    /**
     * Flag indicating if the OTP is for a login attempt (vs registration).
     */
    @com.fasterxml.jackson.annotation.JsonProperty("isLogin")
    private Boolean isLogin;

    /**
     * Device identifier for targeting the OTP or auditing the request.
     */
    private String deviceId;

    private String deviceName;
    private String deviceModel;
    private String osName;
    private String osVersion;
    private String ipAddress;
    private Double latitude;
    private Double longitude;
    private String city;
    private String country;
    private String appVersion;

    /**
     * When true, the backend skips SMS delivery (or suppresses the OTP echo).
     * All validation still runs. Firebase handles SMS on the client side.
     */
    private Boolean skipSms;

    public OtpRequest() {
    }

    public OtpRequest(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public String getTemporaryToken() {
        return temporaryToken;
    }

    public void setTemporaryToken(String temporaryToken) {
        this.temporaryToken = temporaryToken;
    }

    public Boolean getIsLogin() {
        return isLogin;
    }

    public void setIsLogin(Boolean isLogin) {
        this.isLogin = isLogin;
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
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

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public Boolean getSkipSms() {
        return skipSms;
    }

    public void setSkipSms(Boolean skipSms) {
        this.skipSms = skipSms;
    }
}
