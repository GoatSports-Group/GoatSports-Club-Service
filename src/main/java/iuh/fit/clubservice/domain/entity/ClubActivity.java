package iuh.fit.clubservice.domain.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubActivity extends BaseEntity {
    private UUID activityId;
    private UUID clubId;
    private String title;
    private String description;
    private UUID venueId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private UUID createdByUserId;
}
