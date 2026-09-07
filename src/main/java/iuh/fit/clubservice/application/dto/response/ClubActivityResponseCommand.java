package iuh.fit.clubservice.application.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClubActivityResponseCommand {
    private UUID activityId;
    private UUID clubId;
    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID venueId;
    private UUID createdBy;
    private LocalDateTime createdAt;
}
