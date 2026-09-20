package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubFeePaymentRepository;
import iuh.fit.clubservice.domain.entity.ClubFeePayment;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubFeePaymentEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubFeePaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ClubFeePaymentRepositoryImpl implements ClubFeePaymentRepository {
    private final JpaClubFeePaymentRepository jpaRepository;
    private final ClubFeePaymentEntityMapper mapper;

    @Override
    public ClubFeePayment save(ClubFeePayment payment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(payment)));
    }

    @Override
    public Optional<ClubFeePayment> findById(UUID feePaymentId) {
        return jpaRepository.findById(feePaymentId).map(mapper::toDomain);
    }

    @Override
    public Optional<ClubFeePayment> findByFeeIdAndMembershipId(UUID feeId, UUID membershipId) {
        return jpaRepository.findByFeeIdAndMembershipId(feeId, membershipId).map(mapper::toDomain);
    }

    @Override
    public List<ClubFeePayment> findByFeeId(UUID feeId) {
        return jpaRepository.findByFeeIdOrderByCreatedAtAsc(feeId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ClubFeePayment> findByMembershipId(UUID membershipId) {
        return jpaRepository.findByMembershipIdOrderByCreatedAtDesc(membershipId).stream().map(mapper::toDomain).toList();
    }
}
