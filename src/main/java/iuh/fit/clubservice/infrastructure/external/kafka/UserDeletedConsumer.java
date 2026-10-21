package iuh.fit.clubservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.clubservice.application.service.AccountDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** auth-service vừa ẩn danh hóa một tài khoản (hết 30 ngày chờ xóa): cho người đó rời mọi CLB. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "user-deleted.consumer-enabled", havingValue = "true")
public class UserDeletedConsumer {
    private final ObjectMapper objectMapper;
    private final AccountDataService accountDataService;

    @KafkaListener(topics = "${user-deleted.topic}", groupId = "${user-deleted.club-group-id}")
    public void consume(String message) throws Exception {
        UUID userId = UUID.fromString(objectMapper.readTree(message).path("userId").asText());
        accountDataService.eraseUserData(userId);
        log.info("Erased club data of deleted user {}", userId);
    }
}
