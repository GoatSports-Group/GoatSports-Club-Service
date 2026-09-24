package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRegistration extends BaseEntity {
    private UUID registrationId;
    private UUID tournamentId;
    private UUID playerId;
    private UUID clubId;
    private String teamName;
    private RegistrationType type;
    private RegistrationStatus status;
    private UUID registeredBy;
    private SkillLevel skillLevel;
    private String statusReason;
    private LocalDateTime registeredAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime paymentDeadline;
    private List<TournamentLineup> lineups;
}
