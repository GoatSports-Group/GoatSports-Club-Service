package iuh.fit.clubservice.infrastructure.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {
    @Value("${app.gateway.secret}") private String gatewaySecret;

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/internal/") || uri.startsWith("/actuator");
    }

    @Override protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            String secret = request.getHeader("X-Gateway-Secret");
            if (secret == null || !gatewaySecret.equals(secret)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"statusCode\":403,\"message\":\"Forbidden\",\"error\":\"Phải đi qua API Gateway\"}");
                return;
            }
            String userId = request.getHeader("X-User-Id");
            String principal = Optional.ofNullable(request.getHeader("X-User-Email"))
                    .orElse(request.getHeader("X-User-Username"));
            String roles = request.getHeader("X-User-Roles");
            String permissions = request.getHeader("X-User-Authorities");
            List<SimpleGrantedAuthority> authorities = Stream.concat(split(permissions), split(roles)
                            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role))
                    .map(SimpleGrantedAuthority::new).distinct().toList();
            if ((userId != null && !userId.isBlank()) || (principal != null && !principal.isBlank())) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(userId);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            chain.doFilter(request, response);
        } finally { SecurityContextHolder.clearContext(); }
    }

    private Stream<String> split(String raw) {
        return raw == null ? Stream.empty() : Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isBlank());
    }
}
