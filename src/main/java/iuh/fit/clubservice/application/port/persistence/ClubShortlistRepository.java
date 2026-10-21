package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubShortlistEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubShortlistRepository {
    ClubShortlistEntry save(ClubShortlistEntry entry);

    List<ClubShortlistEntry> findByClubId(UUID clubId);

    Optional<ClubShortlistEntry> findByClubIdAndUserId(UUID clubId, UUID userId);

    long countByClubId(UUID clubId);

    void delete(ClubShortlistEntry entry);

    /** Gỡ người dùng khỏi danh sách theo dõi của mọi CLB. */
    void deleteByUserId(UUID userId);
}
