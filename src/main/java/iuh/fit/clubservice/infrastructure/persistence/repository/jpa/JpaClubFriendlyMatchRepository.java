package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFriendlyMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaClubFriendlyMatchRepository extends JpaRepository<ClubFriendlyMatchEntity, UUID> {
    List<ClubFriendlyMatchEntity> findBySessionId(UUID sessionId);

    List<ClubFriendlyMatchEntity> findByClubIdOrderByPlayedAtDesc(UUID clubId);
}
