package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTournamentRequest {
    @NotBlank(message = "Tên giải đấu không được để trống")
    @Size(max = 150, message = "Tên giải đấu tối đa 150 ký tự")
    private String name;
    @Size(max = 3000, message = "Mô tả tối đa 3000 ký tự")
    private String description;
    @NotNull(message = "Bộ môn thể thao không được để trống")
    private SportType sportType;
    private TournamentFormat format;
    @Min(value = 2, message = "Giải đấu cần ít nhất 2 người hoặc đội")
    @Max(value = 128, message = "Giải đấu tối đa 128 người hoặc đội")
    private Integer maxParticipants;
    @PositiveOrZero private Double entryFee;
    @PositiveOrZero private Double prizePool;
    @NotNull private LocalDate registrationOpenDate;
    @NotNull private LocalDate registrationCloseDate;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @Size(max = 20, message = "Điều lệ tối đa 20 mục")
    private List<@NotBlank @Size(max = 300, message = "Mỗi mục điều lệ tối đa 300 ký tự") String> rules;
    @Size(max = 10, message = "Tối đa 10 điều kiện tham dự")
    @Valid private List<EligibilityRuleItem> eligibilityRules;
    private Boolean publish;
    @NotNull(message = "Chọn cơ sở tổ chức giải")
    private UUID venueId;
    @NotEmpty(message = "Chọn ít nhất một sân cho giải")
    @Size(max = 30, message = "Tối đa 30 sân cho một giải")
    private List<@NotNull UUID> courtIds;
    @NotNull(message = "Chọn giờ bắt đầu thi đấu mỗi ngày")
    private LocalTime dailyStartTime;
    @NotNull(message = "Chọn giờ kết thúc thi đấu mỗi ngày")
    private LocalTime dailyEndTime;
    @NotNull(message = "Nhập thời lượng mỗi trận")
    @Min(value = 15, message = "Mỗi trận tối thiểu 15 phút")
    @Max(value = 240, message = "Mỗi trận tối đa 240 phút")
    private Integer matchDurationMinutes;
    @NotBlank(message = "Chọn hình thức thi đấu")
    private String playFormat;

    @Getter
    @Setter
    public static class EligibilityRuleItem {
        @NotNull(message = "Loại điều kiện không được để trống")
        private EligibilityRuleType ruleType;
        @NotNull(message = "Toán tử điều kiện không được để trống")
        private EligibilityRuleOperator operator;
        @NotBlank(message = "Giá trị điều kiện không được để trống")
        private String expectedValue;
    }
}
