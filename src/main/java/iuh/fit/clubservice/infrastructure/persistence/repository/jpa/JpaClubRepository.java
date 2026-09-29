package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaClubRepository extends JpaRepository<ClubEntity, UUID>, JpaSpecificationExecutor<ClubEntity> {

    long countByActiveTrueAndDisbandedAtIsNull();

    long countByDisbandedAtIsNotNull();
}
