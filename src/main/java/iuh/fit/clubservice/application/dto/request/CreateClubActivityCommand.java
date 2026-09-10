package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateClubActivityCommand {
    private UUID clubId;
    private UUID creatorId;
    private String title;
    private String description;
    private UUID venueId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public void validate() throws ServiceException {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new ServiceException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
    }
}
