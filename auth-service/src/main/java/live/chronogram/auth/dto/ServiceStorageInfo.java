package live.chronogram.auth.dto;

public class ServiceStorageInfo {
    private long totalFiles;
    private long totalBytes;

    public ServiceStorageInfo() {}

    public ServiceStorageInfo(long totalFiles, long totalBytes) {
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(long totalFiles) {
        this.totalFiles = totalFiles;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }
}
