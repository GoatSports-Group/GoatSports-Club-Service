package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AddClubPhotosRequest {
    @NotEmpty(message = "Vui lòng chọn ít nhất một ảnh")
    @Size(max = 10, message = "Mỗi lần chỉ được đăng tối đa 10 ảnh")
    private List<@NotBlank(message = "Khóa ảnh không hợp lệ") String> imageKeys;
}
