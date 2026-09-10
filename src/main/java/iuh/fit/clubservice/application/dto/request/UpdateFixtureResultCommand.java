package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFixtureResultCommand {
    private UUID fixtureId;
    private UUID actorUserId;
    private Integer score1;
    private Integer score2;

    public void validate() throws ServiceException {
        if (fixtureId == null || actorUserId == null || score1 == null || score2 == null) {
            throw new ServiceException("Trận đấu, người cập nhật và tỷ số là bắt buộc.");
        }
        if (score1 < 0 || score2 < 0) {
            throw new ServiceException("Tỷ số không được âm.");
        }
    }
}
