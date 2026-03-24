package live.chronogram.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SyncPreferenceRequest {
    
    @NotBlank(message = "Sync mode is required")
    @Size(min = 8, max = 20, message = "Invalid sync mode format")
    private String mode; // WIFI_ONLY, ANY_NETWORK

    public SyncPreferenceRequest() {
    }

    public SyncPreferenceRequest(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
