package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaClubInvitationRepository extends JpaRepository<ClubInvitationEntity, UUID> {

    List<ClubInvitationEntity> findByInviteeIdAndStatusOrderByCreatedAtDesc(
            UUID inviteeId, ClubInvitationStatus status);

    List<ClubInvitationEntity> findByClubIdAndStatusOrderByCreatedAtDesc(
            UUID clubId, ClubInvitationStatus status);

    Optional<ClubInvitationEntity> findByClubIdAndInviteeIdAndStatus(
            UUID clubId, UUID inviteeId, ClubInvitationStatus status);
}
