package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TournamentResponseCommand {
    private UUID tournamentId;
    private UUID organizerId;
    private String name;
    private String description;
    private SportType sportType;
    private TournamentFormat format;
    private TournamentStatus status;
    private Integer maxParticipants;
    private long currentParticipants;
    private Double entryFee;
    private Double prizePool;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> rules;
    private UUID venueId;
    private List<UUID> courtIds;
    private java.time.LocalTime dailyStartTime;
    private java.time.LocalTime dailyEndTime;
    private Integer matchDurationMinutes;
    private String playFormat;
    private String playFormatLabel;
    private ParticipantType participantType;
    private Integer rosterMin;
    private Integer rosterMax;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
