package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentVenueReservationRepository;
import iuh.fit.clubservice.domain.entity.TournamentVenueReservation;
import iuh.fit.clubservice.domain.enums.ReservationStatus;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentVenueReservationEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentVenueReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TournamentVenueReservationRepositoryImpl implements TournamentVenueReservationRepository {
    private static final List<ReservationStatus> ACTIVE =
            List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final JpaTournamentVenueReservationRepository jpaRepository;
    private final TournamentVenueReservationEntityMapper mapper;

    @Override
    public TournamentVenueReservation save(TournamentVenueReservation reservation) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(reservation)));
    }

    @Override
    public Optional<TournamentVenueReservation> findById(UUID reservationId) {
        return jpaRepository.findById(reservationId).map(mapper::toDomain);
    }

    @Override
    public List<TournamentVenueReservation> findByTournamentId(UUID tournamentId) {
        return jpaRepository.findByTournamentIdOrderByPlayDateAscStartTimeAsc(tournamentId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<TournamentVenueReservation> findActiveOverlaps(UUID courtId, LocalDate playDate,
                                                               LocalTime startTime, LocalTime endTime) {
        return jpaRepository.findActiveOverlapsForUpdate(courtId, playDate, startTime, endTime, ACTIVE)
                .stream().map(mapper::toDomain).toList();
    }
}
