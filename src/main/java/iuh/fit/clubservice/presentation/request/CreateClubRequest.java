package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateClubRequest {
    @NotBlank(message = "Tên câu lạc bộ không được để trống")
    @Size(max = 255, message = "Tên câu lạc bộ tối đa 255 ký tự")
    private String name;
    @NotBlank(message = "Mô tả câu lạc bộ không được để trống")
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;
    private String logoUrl;
    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    @Size(max = 64, message = "Mã Tỉnh/Thành phố tối đa 64 ký tự")
    private String city;
    @NotBlank(message = "Địa chỉ sinh hoạt không được để trống")
    @Size(max = 255, message = "Địa chỉ sinh hoạt tối đa 255 ký tự")
    private String location;
    @Size(max = 5, message = "Mỗi câu lạc bộ có tối đa 5 badge")
    private List<@NotBlank(message = "Badge không được để trống")
            @Size(max = 24, message = "Mỗi badge tối đa 24 ký tự") String> tags;
    @NotNull(message = "Bộ môn thể thao không được để trống") private SportType sportType;
    @NotNull(message = "Quyền riêng tư không được để trống") private ClubPrivacy privacy;
    @NotNull(message = "Chế độ duyệt thành viên không được để trống") private ClubApprovalMode approvalMode;
}
