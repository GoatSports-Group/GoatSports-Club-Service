package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateFixtureResultRequest {
    @NotNull @PositiveOrZero private Integer score1;
    @NotNull @PositiveOrZero private Integer score2;
}
