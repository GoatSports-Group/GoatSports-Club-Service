package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentFixtureEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentFixtureRepository;
import lombok.RequiredArgsConstructor;
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
    public void deleteByTournamentId(UUID tournamentId) {
        jpaTournamentFixtureRepository.deleteByTournamentId(tournamentId);
    }
}
