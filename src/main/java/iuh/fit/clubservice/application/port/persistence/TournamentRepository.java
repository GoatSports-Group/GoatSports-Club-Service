package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface TournamentRepository {
    Tournament save(Tournament tournament);

    Optional<Tournament> findById(UUID tournamentId);

    Optional<Tournament> findByIdForUpdate(UUID tournamentId);

    Page<Tournament> searchTournaments(SportType sportType, TournamentStatus status, String keyword, Pageable pageable);

    void deleteById(UUID tournamentId);
}
