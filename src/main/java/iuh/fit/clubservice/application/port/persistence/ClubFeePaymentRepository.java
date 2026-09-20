package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubFeePayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubFeePaymentRepository {
    ClubFeePayment save(ClubFeePayment payment);

    Optional<ClubFeePayment> findById(UUID feePaymentId);

    Optional<ClubFeePayment> findByFeeIdAndMembershipId(UUID feeId, UUID membershipId);

    List<ClubFeePayment> findByFeeId(UUID feeId);

    List<ClubFeePayment> findByMembershipId(UUID membershipId);
}
