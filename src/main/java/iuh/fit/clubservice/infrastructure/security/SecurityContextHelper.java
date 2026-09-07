package iuh.fit.clubservice.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SecurityContextHelper {
    public Optional<UUID> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof String userId) || userId.isBlank()) {
            return Optional.empty();
        }
        try { return Optional.of(UUID.fromString(userId)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
}
