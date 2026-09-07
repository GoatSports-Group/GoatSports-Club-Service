package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateClubRequest {
    @NotBlank(message = "Tên câu lạc bộ không được để trống") private String name;
    private String description;
    private String logoUrl;
    @NotNull(message = "Bộ môn thể thao không được để trống") private SportType sportType;
    private ClubPrivacy privacy;
    private ClubApprovalMode approvalMode;
}
