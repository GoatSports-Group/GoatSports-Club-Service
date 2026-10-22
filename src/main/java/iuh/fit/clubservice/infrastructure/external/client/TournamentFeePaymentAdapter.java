package iuh.fit.clubservice.infrastructure.external.client;

import feign.FeignException;
import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.TournamentFeePayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentFeePaymentAdapter implements TournamentFeePayment {
    private final PaymentClient paymentClient;

    @Override
    public Checkout checkout(UUID registrationId, UUID payerId, double amount, String description,
                             LocalDateTime deadline) throws ServiceException {
        try {
            BaseResponse<PaymentClient.CheckoutView> response = paymentClient.createTournamentFeeCheckout(
                    new PaymentClient.CheckoutPayload(payerId, registrationId, amount, description, deadline));
            PaymentClient.CheckoutView view = response == null ? null : response.getData();
            if (view == null) throw new ServiceException("Cổng thanh toán không trả về mã thanh toán.");
            return new Checkout(view.paymentId(), view.status(), view.checkoutUrl(), view.qrCodeContent(),
                    view.expiresAt());
        } catch (FeignException exception) {
            if (exception.status() >= 400 && exception.status() < 500) {
                throw new ServiceException(RemoteErrors.message(exception, "Không khởi tạo được thanh toán lệ phí."));
            }
            log.warn("Không tạo được checkout lệ phí cho đăng ký {}: {}", registrationId, exception.getMessage());
            throw new ServiceException("Cổng thanh toán đang bận, vui lòng thử lại sau ít phút.");
        } catch (RuntimeException exception) {
            log.warn("Không tạo được checkout lệ phí cho đăng ký {}: {}", registrationId, exception.getMessage());
            throw new ServiceException("Cổng thanh toán đang bận, vui lòng thử lại sau ít phút.");
        }
    }

    @Override
    public void assignPayee(UUID paymentId, UUID organizerId) {
        paymentClient.assignPayee(paymentId, new PaymentClient.PayeePayload(organizerId));
    }

    @Override
    public boolean refund(UUID paymentId, UUID registrationId, UUID requestedBy, double amount, String reason) {
        try {
            paymentClient.createRefund(paymentId, requestedBy, "tournament-refund:" + registrationId,
                    // autoDisburse: có tài khoản ngân hàng thì chuyển ngay; chưa có thì chờ và tự chuyển khi người chơi liên kết.
                    new PaymentClient.RefundPayload(reason, amount, true));
            return true;
        } catch (RuntimeException exception) {
            log.warn("Không gửi được yêu cầu hoàn lệ phí đăng ký {}: {}", registrationId, exception.getMessage());
            return false;
        }
    }
}
