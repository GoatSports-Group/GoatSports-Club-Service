package iuh.fit.clubservice.application.port.security;

import java.util.Optional;
import java.util.UUID;

public interface CurrentUserProvider {
    Optional<UUID> getCurrentUserId();
}
