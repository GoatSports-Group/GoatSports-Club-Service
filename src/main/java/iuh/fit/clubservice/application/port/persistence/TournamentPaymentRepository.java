package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentPayment;
import java.util.Optional;
import java.util.UUID;

public interface TournamentPaymentRepository {
    TournamentPayment save(TournamentPayment payment);
    Optional<TournamentPayment> findByRegistrationId(UUID registrationId);

    java.util.List<TournamentPayment> findByStatus(iuh.fit.clubservice.domain.enums.FeePaymentStatus status);
}
