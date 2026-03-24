package live.chronogram.auth.dto;

import java.time.LocalDate;

public interface UserSummaryProjection {
    Long getUserId();

    String getName();

    String getEmail();

    String getMobileNumber();

    LocalDate getDob();


    Boolean getMobileVerified();

    Boolean getEmailVerified();

    Boolean getIsDeleted();

    UserStatusSummary getUserStatus();

    interface UserStatusSummary {
        String getName();
    }
}
