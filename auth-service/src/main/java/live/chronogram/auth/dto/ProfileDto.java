package live.chronogram.auth.dto;

public class ProfileDto {
    private String name;
    private String email;
    private String publicKey;

    public ProfileDto() {
    }

    public ProfileDto(String name, String email, String publicKey) {
        this.name = name;
        this.email = email;
        this.publicKey = publicKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
