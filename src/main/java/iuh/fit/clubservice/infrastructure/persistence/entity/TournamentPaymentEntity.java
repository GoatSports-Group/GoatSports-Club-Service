package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tournament_payments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentPaymentEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tournament_payment_id")
    private UUID tournamentPaymentId;

    @Column(name = "registration_id", nullable = false, unique = true)
    private UUID registrationId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeePaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
