package iuh.fit.clubservice.domain.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubPhoto extends BaseEntity {
    private UUID photoId;
    private UUID clubId;
    private String imageUrl;
    private UUID uploadedByUserId;
}
