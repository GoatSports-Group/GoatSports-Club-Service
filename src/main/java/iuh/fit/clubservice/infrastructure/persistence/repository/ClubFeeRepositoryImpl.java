package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubFeeRepository;
import iuh.fit.clubservice.domain.entity.ClubFee;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubFeeEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubFeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ClubFeeRepositoryImpl implements ClubFeeRepository {
    private final JpaClubFeeRepository jpaRepository;
    private final ClubFeeEntityMapper mapper;

    @Override
    public ClubFee save(ClubFee fee) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(fee)));
    }

    @Override
    public Optional<ClubFee> findById(UUID feeId) {
        return jpaRepository.findById(feeId).map(mapper::toDomain);
    }

    @Override
    public List<ClubFee> findByClubId(UUID clubId) {
        return jpaRepository.findByClubIdOrderByDueDateAsc(clubId).stream().map(mapper::toDomain).toList();
    }
}
