package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClubActivityCommand {
    private UUID clubId;
    private UUID activityId;
    private UUID actorUserId;
    private String title;
    private String description;
    private UUID venueId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public void validate() throws ServiceException {
        if (title == null || title.isBlank()) {
            throw new ServiceException("Tiêu đề hoạt động không được để trống.");
        }
        if (startAt == null || endAt == null) {
            throw new ServiceException("Hoạt động cần mốc bắt đầu và kết thúc.");
        }
        if (!endAt.isAfter(startAt)) {
            throw new ServiceException("Thời điểm kết thúc phải sau thời điểm bắt đầu.");
        }
    }
}
