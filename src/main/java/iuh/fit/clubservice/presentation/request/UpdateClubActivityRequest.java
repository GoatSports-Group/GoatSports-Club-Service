package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClubActivityRequest {
    @NotBlank(message = "Tiêu đề hoạt động không được để trống")
    private String title;
    private String description;
    private UUID venueId;
    @NotNull(message = "Thời điểm bắt đầu không được để trống")
    private LocalDateTime startAt;
    @NotNull(message = "Thời điểm kết thúc không được để trống")
    private LocalDateTime endAt;
}
