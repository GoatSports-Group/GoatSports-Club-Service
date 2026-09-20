package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubFeeResponseCommand {
    private UUID feeId;
    private UUID clubId;
    private String name;
    private Double amount;
    private LocalDate dueDate;
    private Boolean required;
    private long paidCount;
    private long memberCount;
    private FeePaymentStatus myStatus;
    private LocalDateTime myPaidAt;
}
