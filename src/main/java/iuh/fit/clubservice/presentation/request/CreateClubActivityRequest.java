package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateClubActivityRequest {
    @NotBlank(message = "Tiêu đề không được để trống") private String title;
    private String description;
    private UUID venueId;
    @NotNull(message = "Thời gian bắt đầu không được để trống") private LocalDateTime startAt;
    @NotNull(message = "Thời gian kết thúc không được để trống") private LocalDateTime endAt;
}
