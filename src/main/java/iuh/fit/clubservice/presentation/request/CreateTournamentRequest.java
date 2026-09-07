package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateTournamentRequest {
    @NotBlank(message = "Tên giải đấu không được để trống") private String name;
    private String description;
    @NotNull(message = "Bộ môn thể thao không được để trống") private SportType sportType;
    private TournamentFormat format;
    @Min(value = 2, message = "Giải đấu cần ít nhất 2 người hoặc đội") private Integer maxParticipants;
    @PositiveOrZero private Double entryFee;
    @PositiveOrZero private Double prizePool;
    @NotNull private LocalDate registrationOpenDate;
    @NotNull private LocalDate registrationCloseDate;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private List<String> rules;
}
