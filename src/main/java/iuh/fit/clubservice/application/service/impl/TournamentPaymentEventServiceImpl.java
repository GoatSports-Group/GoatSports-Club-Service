package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.application.service.TournamentPaymentEventService;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentPaymentEventServiceImpl implements TournamentPaymentEventService {
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentPaymentRepository paymentRepository;
    private final ClubConversationSync conversationSync;

    @Override @Transactional
    public void apply(UUID paymentId, UUID payerId, UUID registrationId, Double amount, String currency,
                      boolean succeeded, LocalDateTime paidAt) throws ServiceException {
        if (!"VND".equalsIgnoreCase(currency)) throw new ServiceException("Đơn vị tiền tệ của lệ phí giải đấu không hợp lệ.");
        TournamentRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy đăng ký giải đấu của giao dịch."));
        if (!registration.getRegisteredBy().equals(payerId)) throw new ServiceException("Người thanh toán không khớp người đăng ký.");
        TournamentPayment payment = paymentRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy khoản lệ phí giải đấu."));
        if (Math.abs(payment.getAmount() - amount) > 0.001D) throw new ServiceException("Số tiền thanh toán không khớp lệ phí giải đấu.");
        payment.setPaymentId(paymentId);
        payment.setStatus(succeeded ? FeePaymentStatus.SUCCEEDED : FeePaymentStatus.FAILED);
        payment.setPaidAt(succeeded ? (paidAt == null ? LocalDateTime.now() : paidAt) : null);
        paymentRepository.save(payment);
        if (!succeeded || registration.getStatus() != RegistrationStatus.PENDING_PAYMENT) return;

        // Tien ve sau khi giai da chot danh sach thi khong duoc confirm:
        // bracket sinh tu danh sach CONFIRMED tai thoi diem do, them vao luc nay se tao doi khong co tran nao.
        Tournament tournament = tournamentRepository.findById(registration.getTournamentId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu của đăng ký."));
        if (closedForRegistration(tournament)) {
            registration.setStatus(RegistrationStatus.REJECTED);
            registration.setStatusReason("Thanh toán về sau khi giải đã chốt danh sách, cần hoàn lệ phí.");
            registrationRepository.save(registration);
            return;
        }

        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setConfirmedAt(LocalDateTime.now());
        registrationRepository.save(registration);

        // Doi vua tra tien xong moi thuc su vao giai, nen den luc nay chat nhom moi co ho.
        List<UUID> participants = registrationRepository.findByTournamentId(tournament.getTournamentId()).stream()
                .filter(item -> item.getStatus() == RegistrationStatus.CONFIRMED)
                .map(TournamentRegistration::getRegisteredBy)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        conversationSync.syncTournament(tournament, participants);
    }

    private boolean closedForRegistration(Tournament tournament) {
        TournamentStatus status = tournament.getStatus();
        if (status == TournamentStatus.IN_PROGRESS || status == TournamentStatus.COMPLETED
                || status == TournamentStatus.CANCELLED) {
            return true;
        }
        return tournament.getRegistrationCloseDate() != null
                && LocalDate.now().isAfter(tournament.getRegistrationCloseDate());
    }
}
