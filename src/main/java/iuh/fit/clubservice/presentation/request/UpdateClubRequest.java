package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClubRequest {
    @Size(max = 255, message = "Tên câu lạc bộ tối đa 255 ký tự")
    private String name;
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;
    private String logoUrl;
    private String city;
    private String location;
    private ClubPrivacy privacy;
    private ClubApprovalMode approvalMode;
}
