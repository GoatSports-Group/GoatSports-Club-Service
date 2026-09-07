package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "club_fee_payments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubFeePaymentEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fee_payment_id")
    private UUID feePaymentId;

    @Column(name = "fee_id", nullable = false)
    private UUID feeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_id", insertable = false, updatable = false)
    private ClubFeeEntity fee;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", insertable = false, updatable = false)
    private ClubMemberEntity membership;

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
