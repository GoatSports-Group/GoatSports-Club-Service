package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

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
    @Size(max = 1000, message = "Đường dẫn ảnh đại diện tối đa 1000 ký tự")
    private String logoUrl;
    @Size(max = 1000, message = "Đường dẫn ảnh bìa tối đa 1000 ký tự")
    private String bannerUrl;
    private String city;
    private String location;
    @Size(max = 5, message = "Mỗi câu lạc bộ có tối đa 5 badge")
    private List<@jakarta.validation.constraints.NotBlank(message = "Badge không được để trống")
            @Size(max = 24, message = "Mỗi badge tối đa 24 ký tự") String> tags;
    private ClubPrivacy privacy;
    private ClubApprovalMode approvalMode;
}
