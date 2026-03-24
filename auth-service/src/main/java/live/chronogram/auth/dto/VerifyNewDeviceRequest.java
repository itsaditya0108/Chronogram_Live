package live.chronogram.auth.dto;

/**
 * Data Transfer Object for verifying a login attempt from a new/untrusted
 * device.
 */
public class VerifyNewDeviceRequest {
    private String mobileNumber;
    private String otp;
    private String deviceId;
    private String deviceName;
    private String deviceModel;
    private String osName;
    private String osVersion;
    private String appVersion;
    private Double latitude;
    private Double longitude;
    private String country;
    private String city;
    private boolean logoutOtherDevices;
    private String temporaryToken;

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }

    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public boolean isLogoutOtherDevices() { return logoutOtherDevices; }
    public void setLogoutOtherDevices(boolean logoutOtherDevices) { this.logoutOtherDevices = logoutOtherDevices; }

    public String getTemporaryToken() { return temporaryToken; }
    public void setTemporaryToken(String temporaryToken) { this.temporaryToken = temporaryToken; }
}
