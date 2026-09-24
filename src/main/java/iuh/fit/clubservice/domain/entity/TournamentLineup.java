package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.LineupRole;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentLineup extends BaseEntity {
    private UUID lineupId;
    private UUID registrationId;
    private UUID playerId;
    private String playerName;
    private LineupRole lineupRole;
    private Integer shirtNumber;
    private LineupMemberStatus memberStatus;
    private LocalDateTime respondedAt;
}
