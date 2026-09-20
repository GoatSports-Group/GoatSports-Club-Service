package iuh.fit.clubservice.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class SocialClientConfig {
    @Bean
    RequestInterceptor socialServiceSecurityInterceptor(
            @Value("${app.gateway.secret}") String gatewaySecret
    ) {
        return template -> template.header("X-Gateway-Secret", gatewaySecret);
    }
}
