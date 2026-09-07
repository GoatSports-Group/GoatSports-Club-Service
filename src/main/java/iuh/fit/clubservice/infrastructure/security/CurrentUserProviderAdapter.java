package iuh.fit.clubservice.infrastructure.security;

import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CurrentUserProviderAdapter implements CurrentUserProvider {
    private final SecurityContextHelper helper;
    @Override public Optional<UUID> getCurrentUserId() { return helper.getCurrentUserId(); }
}
