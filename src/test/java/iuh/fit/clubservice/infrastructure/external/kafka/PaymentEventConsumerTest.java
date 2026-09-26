package iuh.fit.clubservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsPaidAtSentAsNumberArray() throws Exception {
        // Đúng dạng payment-service đang gửi qua Kafka (JsonSerializer mặc định).
        var node = mapper.readTree("[2026,9,26,18,7,31,676603600]");
        assertThat(PaymentEventConsumer.dateTime(node)).isEqualTo(LocalDateTime.of(2026, 9, 26, 18, 7, 31, 676603600));
    }

    @Test
    void readsPaidAtSentAsText() throws Exception {
        assertThat(PaymentEventConsumer.dateTime(mapper.readTree("\"2026-09-26T18:07:31\"")))
                .isEqualTo(LocalDateTime.of(2026, 9, 26, 18, 7, 31));
        assertThat(PaymentEventConsumer.dateTime(mapper.readTree("\"2026-09-26 18:07:31\"")))
                .isEqualTo(LocalDateTime.of(2026, 9, 26, 18, 7, 31));
    }

    @Test
    void missingPaidAtIsNull() throws Exception {
        assertThat(PaymentEventConsumer.dateTime(mapper.readTree("{}").path("paidAt"))).isNull();
        assertThat(PaymentEventConsumer.dateTime(mapper.readTree("null"))).isNull();
    }
}
