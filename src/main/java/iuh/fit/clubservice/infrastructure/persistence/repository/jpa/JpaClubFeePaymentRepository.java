package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFeePaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaClubFeePaymentRepository extends JpaRepository<ClubFeePaymentEntity, UUID> {
    Optional<ClubFeePaymentEntity> findByFeeIdAndMembershipId(UUID feeId, UUID membershipId);

    List<ClubFeePaymentEntity> findByFeeIdOrderByCreatedAtAsc(UUID feeId);

    List<ClubFeePaymentEntity> findByMembershipIdOrderByCreatedAtDesc(UUID membershipId);
}
