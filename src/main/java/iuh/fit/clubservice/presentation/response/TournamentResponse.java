package iuh.fit.clubservice.presentation.response;

import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentResponse {
    private UUID tournamentId;
    private String name;
    private String description;
    private String bannerUrl;
    private String sportType;
    private TournamentFormat format;
    private TournamentStatus status;
    private UUID organizerId;
    private String organizerName;
    private UUID venueId;
    private String venueName;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private BigDecimal entryFee;
    private BigDecimal prizePool;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String rules;
    private String prizeInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
