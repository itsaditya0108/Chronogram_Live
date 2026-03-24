package live.chronogram.auth.dto;

public class StorageDetailsResponse {
    private Double photos;
    private Double videos;
    private String unit;

    public StorageDetailsResponse() {
    }

    public StorageDetailsResponse(Double photos, Double videos, String unit) {
        this.photos = photos;
        this.videos = videos;
        this.unit = unit;
    }

    public Double getPhotos() {
        return photos;
    }

    public void setPhotos(Double photos) {
        this.photos = photos;
    }

    public Double getVideos() {
        return videos;
    }

    public void setVideos(Double videos) {
        this.videos = videos;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
