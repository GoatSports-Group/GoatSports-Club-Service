package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateClubRequest {
    @NotBlank(message = "Tên câu lạc bộ không được để trống") private String name;
    private String description;
    private String logoUrl;
    private String city;
    private String location;
    @Size(max = 5, message = "Mỗi câu lạc bộ có tối đa 5 badge")
    private List<@NotBlank(message = "Badge không được để trống")
            @Size(max = 24, message = "Mỗi badge tối đa 24 ký tự") String> tags;
    @NotNull(message = "Bộ môn thể thao không được để trống") private SportType sportType;
    private ClubPrivacy privacy;
    private ClubApprovalMode approvalMode;
}
