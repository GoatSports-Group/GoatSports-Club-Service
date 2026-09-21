package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClubCommand {
    private UUID clubId;
    private UUID actorUserId;
    private String name;
    private String description;
    private String logoUrl;
    private String city;
    private String location;
    private List<String> tags;
    private ClubPrivacy privacy;
    private ClubApprovalMode approvalMode;

    public void validate() throws ServiceException {
        if (name != null && name.isBlank()) {
            throw new ServiceException("Tên câu lạc bộ không được để trống.");
        }
    }
}
