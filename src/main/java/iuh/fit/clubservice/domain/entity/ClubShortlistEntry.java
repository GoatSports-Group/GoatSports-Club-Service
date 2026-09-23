package iuh.fit.clubservice.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClubShortlistEntry extends BaseEntity {
    private UUID entryId;
    private UUID clubId;
    private UUID userId;
    private String note;
    private UUID addedBy;
}
