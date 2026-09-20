package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.TournamentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeTournamentStatusRequest {
    @NotNull(message = "Trạng thái giải đấu không được để trống")
    private TournamentStatus status;
}
