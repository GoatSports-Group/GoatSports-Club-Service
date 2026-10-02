package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import java.util.List;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaClubRepository extends JpaRepository<ClubEntity, UUID>, JpaSpecificationExecutor<ClubEntity> {

    List<ClubEntity> findByActiveTrueAndDisbandedAtIsNullAndPrivacy(ClubPrivacy privacy);

    long countByActiveTrueAndDisbandedAtIsNull();

    long countByDisbandedAtIsNotNull();
}
