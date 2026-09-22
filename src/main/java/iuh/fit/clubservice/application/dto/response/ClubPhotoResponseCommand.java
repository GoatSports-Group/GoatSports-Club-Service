package iuh.fit.clubservice.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubPhotoResponseCommand {
    private UUID photoId;
    private UUID clubId;
    private String imageUrl;
    private UUID uploadedByUserId;
    private LocalDateTime createdAt;
}
