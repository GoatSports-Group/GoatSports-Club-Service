package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaTournamentRepository extends JpaRepository<TournamentEntity, UUID>, JpaSpecificationExecutor<TournamentEntity> {
    @Query("SELECT t FROM TournamentEntity t WHERE (:sportType IS NULL OR t.sportType = :sportType) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<TournamentEntity> searchTournaments(
            @Param("sportType") SportType sportType,
            @Param("status") TournamentStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
