package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShareHighlightRequest(
        @NotBlank(message = "Tiêu đề không được để trống") @Size(max = 120, message = "Tiêu đề tối đa 120 ký tự") String title,
        @Size(max = 2000, message = "Nội dung tối đa 2000 ký tự") String content
) {
}
