package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClubFeeRequest {
    @NotBlank(message = "Tên khoản phí không được để trống")
    @Size(max = 255, message = "Tên khoản phí tối đa 255 ký tự")
    private String name;

    @NotNull(message = "Số tiền không được để trống")
    @PositiveOrZero(message = "Số tiền không được âm")
    private Double amount;

    @NotNull(message = "Hạn đóng phí không được để trống")
    private LocalDate dueDate;

    private Boolean required;
}
