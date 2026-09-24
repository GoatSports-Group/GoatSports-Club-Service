package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Tournament extends BaseEntity {
    private UUID tournamentId;
    private String name;
    private String description;
    private SportType sportType;
    private TournamentFormat format;
    private TournamentStatus status;
    private UUID organizerId;
    private Integer maxParticipants;
    private Double entryFee;
    private Double prizePool;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> rules;
    private UUID venueId;
    private List<UUID> courtIds;
    private LocalTime dailyStartTime;
    private LocalTime dailyEndTime;
    private Integer matchDurationMinutes;
    private String playFormat;
}
