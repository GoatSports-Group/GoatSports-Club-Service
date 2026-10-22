package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentPaymentRepository {
    TournamentPayment save(TournamentPayment payment);
    Optional<TournamentPayment> findByRegistrationId(UUID registrationId);

    List<TournamentPayment> findByStatus(FeePaymentStatus status);
}
