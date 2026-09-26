package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaTournamentPaymentRepository extends JpaRepository<TournamentPaymentEntity, UUID> {
    Optional<TournamentPaymentEntity> findByRegistrationId(UUID registrationId);

    java.util.List<TournamentPaymentEntity> findByStatus(iuh.fit.clubservice.domain.enums.FeePaymentStatus status);
}
