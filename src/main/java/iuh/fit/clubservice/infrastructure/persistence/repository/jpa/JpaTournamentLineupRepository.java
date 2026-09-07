package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentLineupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaTournamentLineupRepository extends JpaRepository<TournamentLineupEntity, UUID> {
    List<TournamentLineupEntity> findByRegistrationId(UUID registrationId);
    void deleteByRegistrationId(UUID registrationId);
}
