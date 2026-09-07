package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentPayment extends BaseEntity {
    private UUID tournamentPaymentId;
    private UUID registrationId;
    private Double amount;
    private UUID paymentId;
    private FeePaymentStatus status;
    private LocalDateTime paidAt;
}
