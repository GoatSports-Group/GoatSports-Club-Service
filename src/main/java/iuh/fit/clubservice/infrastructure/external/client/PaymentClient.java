package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.infrastructure.config.PaymentClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-PAYMENT-SERVICE",
        url = "${payment.service.url}",
        path = "/internal/payments",
        configuration = PaymentClientConfig.class
)
public interface PaymentClient {

    @PostMapping("/tournament-fee-checkouts")
    BaseResponse<CheckoutView> createTournamentFeeCheckout(@RequestBody CheckoutPayload payload);

    @PostMapping("/{paymentId}/refunds")
    void createRefund(@PathVariable("paymentId") UUID paymentId,
                      @RequestHeader("X-User-Id") UUID requestedBy,
                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                      @RequestBody RefundPayload payload);

    @PostMapping("/{paymentId}/payee")
    void assignPayee(@PathVariable("paymentId") UUID paymentId, @RequestBody PayeePayload payload);

    record PayeePayload(UUID payeeId) { }

    record CheckoutPayload(UUID payerId, UUID registrationId, Double amount, String description,
                           LocalDateTime expiresAt) { }

    record CheckoutView(UUID paymentId, String status, String checkoutUrl, String deeplink,
                        String qrCodeContent, LocalDateTime expiresAt) { }

    record RefundPayload(String reason, Double amount, Boolean autoDisburse) { }
}
