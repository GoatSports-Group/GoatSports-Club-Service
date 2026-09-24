package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentLineupEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentLineupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TournamentLineupRepositoryImpl implements TournamentLineupRepository {
    private final JpaTournamentLineupRepository jpaTournamentLineupRepository;
    private final TournamentLineupEntityMapper tournamentLineupEntityMapper;

    @Override
    public TournamentLineup save(TournamentLineup lineup) {
        return tournamentLineupEntityMapper.toDomain(jpaTournamentLineupRepository.save(tournamentLineupEntityMapper.toEntity(lineup)));
    }

    @Override
    public List<TournamentLineup> saveAll(List<TournamentLineup> lineups) {
        return jpaTournamentLineupRepository.saveAll(lineups.stream().map(tournamentLineupEntityMapper::toEntity).collect(Collectors.toList()))
                .stream().map(tournamentLineupEntityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<TournamentLineup> findByRegistrationId(UUID registrationId) {
        return jpaTournamentLineupRepository.findByRegistrationId(registrationId).stream()
                .map(tournamentLineupEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TournamentLineup> findByPlayerIdAndMemberStatus(UUID playerId,
            iuh.fit.clubservice.domain.enums.LineupMemberStatus memberStatus) {
        return jpaTournamentLineupRepository.findByPlayerIdAndMemberStatus(playerId, memberStatus).stream()
                .map(tournamentLineupEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID lineupId) {
        jpaTournamentLineupRepository.deleteById(lineupId);
    }

    @Override
    public void deleteByRegistrationId(UUID registrationId) {
        jpaTournamentLineupRepository.deleteByRegistrationId(registrationId);
    }
}
