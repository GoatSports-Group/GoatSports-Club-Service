package iuh.fit.clubservice.presentation.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubActivityResponse {
    private UUID activityId;
    private UUID clubId;
    private String title;
    private String description;
    private UUID venueId;
    private String venueName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private String status;
    private LocalDateTime createdAt;
}
