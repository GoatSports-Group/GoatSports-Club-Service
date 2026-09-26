package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEntity;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TournamentRepositoryImpl implements TournamentRepository {
    private final JpaTournamentRepository jpaTournamentRepository;
    private final TournamentEntityMapper tournamentEntityMapper;

    @Override
    public Tournament save(Tournament tournament) {
        return tournamentEntityMapper.toDomain(jpaTournamentRepository.save(tournamentEntityMapper.toEntity(tournament)));
    }

    @Override
    public Optional<Tournament> findById(UUID tournamentId) {
        return jpaTournamentRepository.findById(tournamentId).map(tournamentEntityMapper::toDomain);
    }

    @Override
    public Optional<Tournament> findByIdForUpdate(UUID tournamentId) {
        return jpaTournamentRepository.findByIdForUpdate(tournamentId).map(tournamentEntityMapper::toDomain);
    }

    private static final List<TournamentStatus> DATE_DRIVEN = List.of(
            TournamentStatus.PUBLISHED, TournamentStatus.REGISTRATION_OPEN, TournamentStatus.REGISTRATION_CLOSED);

    @Override
    public Page<Tournament> searchTournaments(SportType sportType, TournamentStatus status, String keyword,
                                              LocalDate today, Pageable pageable) {
        // Trang khám phá chỉ liệt kê giải còn ý nghĩa với người chơi: sắp mở, đang mở, đã đóng đăng ký,
        // đang diễn ra và đã kết thúc. Bản nháp và giải đã hủy không hiện.
        Specification<TournamentEntity> specification = (root, query, cb) ->
                cb.not(root.get("status").in(TournamentStatus.DRAFT, TournamentStatus.CANCELLED));

        if (sportType != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("sportType"), sportType));
        }
        if (status != null) {
            specification = specification.and(effectiveStatus(status, today));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        return jpaTournamentRepository.findAll(specification, pageable).map(tournamentEntityMapper::toDomain);
    }

    /** Cùng luật với TournamentLifecyclePolicy.effectiveStatus, dịch sang SQL để phân trang đúng. */
    private Specification<TournamentEntity> effectiveStatus(TournamentStatus status, LocalDate today) {
        return switch (status) {
            case PUBLISHED -> (root, query, cb) -> cb.and(root.get("status").in(DATE_DRIVEN),
                    cb.greaterThan(root.get("registrationOpenDate"), today));
            case REGISTRATION_OPEN -> (root, query, cb) -> cb.and(root.get("status").in(DATE_DRIVEN),
                    cb.lessThanOrEqualTo(root.get("registrationOpenDate"), today),
                    cb.greaterThanOrEqualTo(root.get("registrationCloseDate"), today));
            case REGISTRATION_CLOSED -> (root, query, cb) -> cb.and(root.get("status").in(DATE_DRIVEN),
                    cb.lessThan(root.get("registrationCloseDate"), today));
            default -> (root, query, cb) -> cb.equal(root.get("status"), status);
        };
    }

    @Override
    public Page<Tournament> findByOrganizerId(UUID organizerId, Pageable pageable) {
        Specification<TournamentEntity> specification = (root, query, cb) ->
                cb.equal(root.get("organizerId"), organizerId);
        return jpaTournamentRepository.findAll(specification, pageable).map(tournamentEntityMapper::toDomain);
    }

    @Override
    public Page<Tournament> findByIds(Collection<UUID> tournamentIds, Pageable pageable) {
        if (tournamentIds.isEmpty()) return Page.empty(pageable);
        Specification<TournamentEntity> specification = (root, query, cb) -> cb.and(
                root.get("tournamentId").in(tournamentIds),
                cb.notEqual(root.get("status"), TournamentStatus.DRAFT));
        return jpaTournamentRepository.findAll(specification, pageable).map(tournamentEntityMapper::toDomain);
    }

    @Override
    public void deleteById(UUID tournamentId) {
        jpaTournamentRepository.deleteById(tournamentId);
    }
}
