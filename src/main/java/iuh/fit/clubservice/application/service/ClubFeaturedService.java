package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.response.FeaturedClubResponseCommand;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.UUID;

public interface ClubFeaturedService {
    Page<FeaturedClubResponseCommand> getFeatured(UUID viewerId, Collection<SportType> sports,
            Collection<String> cities, SportType filterSport, String filterCity, Pageable pageable);
}
