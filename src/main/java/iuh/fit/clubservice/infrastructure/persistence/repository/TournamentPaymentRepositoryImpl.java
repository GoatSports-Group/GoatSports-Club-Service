package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentPaymentEntity;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TournamentPaymentRepositoryImpl implements TournamentPaymentRepository {
    private final JpaTournamentPaymentRepository repository;
    @Override public TournamentPayment save(TournamentPayment payment) { return toDomain(repository.save(toEntity(payment))); }
    @Override public Optional<TournamentPayment> findByRegistrationId(UUID registrationId) {
        return repository.findByRegistrationId(registrationId).map(this::toDomain);
    }
    @Override public List<TournamentPayment> findByStatus(FeePaymentStatus status) {
        return repository.findByStatus(status).stream().map(this::toDomain).toList();
    }
    private TournamentPaymentEntity toEntity(TournamentPayment value) {
        return TournamentPaymentEntity.builder().tournamentPaymentId(value.getTournamentPaymentId())
                .registrationId(value.getRegistrationId()).amount(value.getAmount()).paymentId(value.getPaymentId())
                .status(value.getStatus()).paidAt(value.getPaidAt()).createdAt(value.getCreatedAt())
                .createdBy(value.getCreatedBy()).updatedAt(value.getUpdatedAt()).updatedBy(value.getUpdatedBy()).build();
    }
    private TournamentPayment toDomain(TournamentPaymentEntity value) {
        return TournamentPayment.builder().tournamentPaymentId(value.getTournamentPaymentId())
                .registrationId(value.getRegistrationId()).amount(value.getAmount()).paymentId(value.getPaymentId())
                .status(value.getStatus()).paidAt(value.getPaidAt()).createdAt(value.getCreatedAt())
                .createdBy(value.getCreatedBy()).updatedAt(value.getUpdatedAt()).updatedBy(value.getUpdatedBy()).build();
    }
}
