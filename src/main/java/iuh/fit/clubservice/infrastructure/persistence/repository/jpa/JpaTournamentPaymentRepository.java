package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaTournamentPaymentRepository extends JpaRepository<TournamentPaymentEntity, UUID> {
    Optional<TournamentPaymentEntity> findByRegistrationId(UUID registrationId);

    List<TournamentPaymentEntity> findByStatus(FeePaymentStatus status);
}
