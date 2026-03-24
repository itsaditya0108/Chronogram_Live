package live.chronogram.auth.dto;

public class StorageUsageResponse {
    private Double used;
    private Double limit;
    private String unit;
    private boolean warning;

    public StorageUsageResponse() {}

    public StorageUsageResponse(Double used, Double limit, String unit, boolean warning) {
        this.used = used;
        this.limit = limit;
        this.unit = unit;
        this.warning = warning;
    }

    public Double getUsed() {
        return used;
    }

    public void setUsed(Double used) {
        this.used = used;
    }

    public Double getLimit() {
        return limit;
    }

    public void setLimit(Double limit) {
        this.limit = limit;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public boolean isWarning() {
        return warning;
    }

    public void setWarning(boolean warning) {
        this.warning = warning;
    }
}
