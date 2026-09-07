package iuh.fit.clubservice.application.dto.request;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateClubActivityCommand {
    private UUID clubId;
    private UUID creatorId;
    private String title;
    private String description;
    private UUID venueId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
