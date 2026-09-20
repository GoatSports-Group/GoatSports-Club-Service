package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaClubFeeRepository extends JpaRepository<ClubFeeEntity, UUID> {
    List<ClubFeeEntity> findByClubIdOrderByDueDateAsc(UUID clubId);
}
