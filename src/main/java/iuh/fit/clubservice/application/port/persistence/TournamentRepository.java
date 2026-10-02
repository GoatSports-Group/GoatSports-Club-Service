package iuh.fit.clubservice.application.port.persistence;

import java.util.List;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TournamentRepository {
    Tournament save(Tournament tournament);

    Optional<Tournament> findById(UUID tournamentId);

    Optional<Tournament> findByIdForUpdate(UUID tournamentId);

    Page<Tournament> searchTournaments(SportType sportType, TournamentStatus status, String keyword,
                                       LocalDate today, Pageable pageable);

    Page<Tournament> findByOrganizerId(UUID organizerId, Pageable pageable);

    /** Giải của một người tổ chức, lọc theo trạng thái hiệu lực (kể cả nháp/hủy), môn, cơ sở và tên. */
    Page<Tournament> searchOrganized(UUID organizerId, TournamentStatus status, SportType sportType, UUID venueId,
                                     String keyword, LocalDate today, Pageable pageable);

    /** Giai trong `tournamentIds` (giai nguoi choi tham gia), cung bo loc voi trang kham pha; null = khong loc. */
    Page<Tournament> searchParticipating(Collection<UUID> tournamentIds, TournamentStatus status, SportType sportType,
                                         String keyword, LocalDate today, Pageable pageable);

    void deleteById(UUID tournamentId);

    /** Mọi giải (kể cả nháp, đã hủy): cho thống kê toàn nền tảng. */
    List<Tournament> findAll();
}
