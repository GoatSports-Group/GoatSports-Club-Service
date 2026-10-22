package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentFixtureEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentFixtureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TournamentFixtureRepositoryImpl implements TournamentFixtureRepository {
    private final JpaTournamentFixtureRepository jpaTournamentFixtureRepository;
    private final TournamentFixtureEntityMapper tournamentFixtureEntityMapper;

    @Override
    public TournamentFixture save(TournamentFixture fixture) {
        return tournamentFixtureEntityMapper.toDomain(jpaTournamentFixtureRepository.save(tournamentFixtureEntityMapper.toEntity(fixture)));
    }

    @Override
    public List<TournamentFixture> saveAll(List<TournamentFixture> fixtures) {
        return jpaTournamentFixtureRepository.saveAll(fixtures.stream().map(tournamentFixtureEntityMapper::toEntity).collect(Collectors.toList()))
                .stream().map(tournamentFixtureEntityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<TournamentFixture> findById(UUID fixtureId) {
        return jpaTournamentFixtureRepository.findById(fixtureId).map(tournamentFixtureEntityMapper::toDomain);
    }

    @Override
    public List<TournamentFixture> findByTournamentId(UUID tournamentId) {
        return jpaTournamentFixtureRepository.findByTournamentIdOrderByRoundNumberAscMatchNumberAsc(tournamentId).stream()
                .map(tournamentFixtureEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TournamentFixture> findRatingPending(int limit) {
        return jpaTournamentFixtureRepository.findByRatingSyncedFalse(PageRequest.of(0, limit))
                .stream().map(tournamentFixtureEntityMapper::toDomain).toList();
    }

    @Override
    public boolean markRatingSynced(UUID fixtureId, Integer score1, Integer score2) {
        return jpaTournamentFixtureRepository.markRatingSynced(fixtureId, score1, score2) > 0;
    }

    @Override
    public void deleteByTournamentId(UUID tournamentId) {
        jpaTournamentFixtureRepository.deleteByTournamentId(tournamentId);
    }
}
