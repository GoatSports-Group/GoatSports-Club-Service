package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubFriendlyMatchRepository;
import iuh.fit.clubservice.domain.entity.ClubFriendlyMatch;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubFriendlyMatchEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubFriendlyMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ClubFriendlyMatchRepositoryImpl implements ClubFriendlyMatchRepository {
    private final JpaClubFriendlyMatchRepository jpaRepository;
    private final ClubFriendlyMatchEntityMapper mapper;

    @Override
    public List<ClubFriendlyMatch> saveAll(List<ClubFriendlyMatch> matches) {
        return jpaRepository.saveAll(matches.stream().map(mapper::toEntity).toList()).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public List<ClubFriendlyMatch> findBySessionId(UUID sessionId) {
        return jpaRepository.findBySessionId(sessionId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ClubFriendlyMatch> findByClubId(UUID clubId) {
        return jpaRepository.findByClubIdOrderByPlayedAtDesc(clubId).stream().map(mapper::toDomain).toList();
    }
}
