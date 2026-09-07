package iuh.fit.clubservice.infrastructure.persistence.audit;

import iuh.fit.clubservice.infrastructure.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component("auditorAware")
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<String> {
    private final SecurityContextHelper helper;
    @Override public Optional<String> getCurrentAuditor() { return helper.getCurrentUserId().map(Object::toString); }
}
