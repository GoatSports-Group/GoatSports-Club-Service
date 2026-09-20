package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubFeePaymentResponseCommand {
    private UUID feePaymentId;
    private UUID feeId;
    private UUID membershipId;
    private UUID userId;
    private Double amount;
    private UUID paymentId;
    private FeePaymentStatus status;
    private LocalDateTime paidAt;
}
