package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubInvitationEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ClubInvitationRepositoryImpl implements ClubInvitationRepository {
    private final JpaClubInvitationRepository jpaRepository;
    private final ClubInvitationEntityMapper mapper;

    @Override
    public ClubInvitation save(ClubInvitation invitation) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(invitation)));
    }

    @Override
    public Optional<ClubInvitation> findById(UUID invitationId) {
        return jpaRepository.findById(invitationId).map(mapper::toDomain);
    }

    @Override
    public List<ClubInvitation> findByInviteeIdAndStatus(UUID inviteeId, ClubInvitationStatus status) {
        return jpaRepository.findByInviteeIdAndStatusOrderByCreatedAtDesc(inviteeId, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ClubInvitation> findByClubIdAndStatus(UUID clubId, ClubInvitationStatus status) {
        return jpaRepository.findByClubIdAndStatusOrderByCreatedAtDesc(clubId, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ClubInvitation> findRecentByClubId(UUID clubId, int limit) {
        return jpaRepository.findByClubIdOrderByCreatedAtDesc(clubId, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<ClubInvitation> findByClubIdAndInviteeIdAndStatus(
            UUID clubId, UUID inviteeId, ClubInvitationStatus status) {
        return jpaRepository.findByClubIdAndInviteeIdAndStatus(clubId, inviteeId, status)
                .map(mapper::toDomain);
    }
}
