package iuh.fit.clubservice.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ShortlistEntryResponseCommand {
    private UUID userId;
    private String note;
    private UUID addedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private PlayerClubRelation relation;
    private ScoutedPlayerResponseCommand player;
}
