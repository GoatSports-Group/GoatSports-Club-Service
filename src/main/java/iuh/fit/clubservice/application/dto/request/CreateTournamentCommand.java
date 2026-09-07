package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateTournamentCommand {
    private UUID organizerId;
    private String name;
    private String description;
    private SportType sportType;
    private TournamentFormat format;
    private Integer maxParticipants;
    private Double entryFee;
    private Double prizePool;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> rules;
}
