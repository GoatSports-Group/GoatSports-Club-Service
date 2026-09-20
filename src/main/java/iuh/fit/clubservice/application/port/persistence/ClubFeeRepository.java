package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubFee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubFeeRepository {
    ClubFee save(ClubFee fee);

    Optional<ClubFee> findById(UUID feeId);

    List<ClubFee> findByClubId(UUID clubId);
}
