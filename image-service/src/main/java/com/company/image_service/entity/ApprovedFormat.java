package com.company.image_service.entity;

import jakarta.persistence.*;

/**
 * Entity for global list of allowed image formats.
 * Persisted in approved_formats table across services.
 */
@Entity
@Table(name = "approved_formats")
public class ApprovedFormat {

    @Id
    @Column(name = "extension", length = 20)
    private String extension;

    @Column(name = "format_name", length = 100)
    private String formatName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public String getFormatName() { return formatName; }
    public void setFormatName(String formatName) { this.formatName = formatName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
