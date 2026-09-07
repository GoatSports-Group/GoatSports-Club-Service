package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubEntity;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaClubRepository extends JpaRepository<ClubEntity, UUID>, JpaSpecificationExecutor<ClubEntity> {
    @Query("SELECT c FROM ClubEntity c WHERE (:sportType IS NULL OR c.sportType = :sportType) " +
           "AND c.active = true " +
           "AND (:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ClubEntity> searchClubs(
            @Param("sportType") SportType sportType,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
