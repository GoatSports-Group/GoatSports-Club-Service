package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.external.TournamentFeePayment;
import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentPaymentEventService;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentPaymentEventServiceImpl implements TournamentPaymentEventService {
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentPaymentRepository paymentRepository;
    private final TournamentRegistrationService registrationService;
    private final TournamentFeePayment feePayment;
    private final TournamentEventPublisher eventPublisher;

    @Override @Transactional(rollbackFor = ServiceException.class)
    public void apply(UUID paymentId, UUID payerId, UUID registrationId, Double amount, String currency,
                      boolean succeeded, LocalDateTime paidAt) throws ServiceException {
        if (!"VND".equalsIgnoreCase(currency)) throw new ServiceException("Đơn vị tiền tệ của lệ phí giải đấu không hợp lệ.");
        TournamentRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy đăng ký giải đấu của giao dịch."));
        if (!registration.getRegisteredBy().equals(payerId)) throw new ServiceException("Người thanh toán không khớp người đăng ký.");
        TournamentPayment payment = paymentRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy khoản lệ phí giải đấu."));
        if (Math.abs(payment.getAmount() - amount) > 0.001D) throw new ServiceException("Số tiền thanh toán không khớp lệ phí giải đấu.");
        if (succeeded) {
            // Lệ phí thuộc doanh thu người tổ chức (khoản nào phải hoàn thì payment-service tự trừ lại).
            // Gọi cả ở sự kiện lặp để tự lành nếu lần trước gọi lỗi; lỗi thì ném ra để Kafka xử lý lại.
            tournamentRepository.findById(registration.getTournamentId())
                    .ifPresent(tournament -> feePayment.assignPayee(paymentId, tournament.getOrganizerId()));
        }
        // Su kien lap lai (at-least-once) sau khi da xu ly: bo qua de khong hoan/xac nhan hai lan.
        if (payment.getStatus() == FeePaymentStatus.SUCCEEDED || payment.getStatus() == FeePaymentStatus.REFUND_REQUESTED) {
            return;
        }
        payment.setPaymentId(paymentId);
        payment.setStatus(succeeded ? FeePaymentStatus.SUCCEEDED : FeePaymentStatus.FAILED);
        payment.setPaidAt(succeeded ? (paidAt == null ? LocalDateTime.now() : paidAt) : null);
        paymentRepository.save(payment);
        if (!succeeded) return;

        Tournament tournament = tournamentRepository.findByIdForUpdate(registration.getTournamentId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu của đăng ký."));
        registration = registrationRepository.findById(registrationId).orElse(registration);

        // Tien ve cho dang ky da bi dong (qua han, bi tu choi, rut) hoac giai da chot danh sach: hoan tu dong.
        // Bracket sinh tu danh sach CONFIRMED luc xep lich, them vao luc nay se tao doi khong co tran nao.
        boolean closedTournament = TournamentLifecyclePolicy.isRosterLocked(tournament);
        if (registration.getStatus() != RegistrationStatus.PENDING_PAYMENT || closedTournament) {
            String reason = closedTournament
                    ? "Thanh toán về sau khi giải đã chốt danh sách"
                    : "Thanh toán về sau khi đăng ký đã đóng";
            boolean requested = feePayment.refund(paymentId, registrationId, tournament.getOrganizerId(), amount, reason);
            if (requested) {
                payment.setStatus(FeePaymentStatus.REFUND_REQUESTED);
                paymentRepository.save(payment);
            }
            if (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT) {
                registration.setStatus(RegistrationStatus.REJECTED);
            }
            String note = reason + (requested ? " — đã gửi yêu cầu hoàn lệ phí." : " — cần hoàn lệ phí, ban tổ chức sẽ xử lý.");
            registration.setStatusReason(registration.getStatusReason() == null ? note
                    : registration.getStatusReason() + ". " + note);
            registrationRepository.save(registration);
            eventPublisher.publish(TournamentEventPublisher.CLOSED, tournament, registration.getRegisteredBy(), null,
                    Map.of("registrationId", registrationId.toString(),
                            "teamName", String.valueOf(registration.getTeamName()), "reason", note));
            return;
        }

        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setConfirmedAt(LocalDateTime.now());
        registrationRepository.save(registration);
        // Doi vua tra tien xong moi thuc su vao giai, nen den luc nay chat nhom moi co ho.
        registrationService.syncConversation(tournament);
        eventPublisher.publish(TournamentEventPublisher.CONFIRMED, tournament, registration.getRegisteredBy(), null,
                Map.of("registrationId", registrationId.toString(), "teamName", String.valueOf(registration.getTeamName())));
    }
}
