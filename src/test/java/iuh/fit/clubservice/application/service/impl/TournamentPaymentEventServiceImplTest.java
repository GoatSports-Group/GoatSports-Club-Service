package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.external.TournamentFeePayment;
import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentPaymentEventServiceImplTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentPaymentRepository paymentRepository;
    @Mock private TournamentRegistrationService registrationService;
    @Mock private TournamentFeePayment feePayment;
    @Mock private TournamentEventPublisher eventPublisher;

    private TournamentPaymentEventServiceImpl service;

    private final UUID tournamentId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID payerId = UUID.randomUUID();
    private TournamentRegistration registration;
    private TournamentPayment payment;

    @BeforeEach
    void setUp() {
        service = new TournamentPaymentEventServiceImpl(tournamentRepository, registrationRepository, paymentRepository,
                registrationService, feePayment, eventPublisher);

        registration = TournamentRegistration.builder()
                .registrationId(registrationId).tournamentId(tournamentId).registeredBy(payerId)
                .type(RegistrationType.INDIVIDUAL).status(RegistrationStatus.PENDING_PAYMENT)
                .paymentDeadline(LocalDateTime.now().plusHours(3)).build();
        payment = TournamentPayment.builder().registrationId(registrationId).amount(100_000D)
                .status(FeePaymentStatus.PENDING).build();

        when(registrationRepository.findById(registrationId)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(feePayment.refund(any(), any(), any(), anyDouble(), anyString())).thenReturn(true);
    }

    private void tournament(TournamentStatus status, LocalDate closeDate) {
        Tournament tournament = Tournament.builder()
                .tournamentId(tournamentId).organizerId(UUID.randomUUID()).name("Giải test")
                .sportType(SportType.FOOTBALL).format(TournamentFormat.ROUND_ROBIN).status(status)
                .maxParticipants(8).entryFee(100_000D).prizePool(0D)
                .registrationOpenDate(LocalDate.now().minusDays(5))
                .registrationCloseDate(closeDate)
                .startDate(LocalDate.now().plusDays(5)).endDate(LocalDate.now().plusDays(6))
                .build();
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.findByIdForUpdate(tournamentId)).thenReturn(Optional.of(tournament));
    }

    @Test
    @DisplayName("thanh toán trong hạn thì xác nhận đăng ký và đưa vào chat nhóm")
    void paymentInTimeConfirmsRegistration() throws ServiceException {
        tournament(TournamentStatus.REGISTRATION_OPEN, LocalDate.now().plusDays(2));

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registration.getConfirmedAt()).isNotNull();
        verify(registrationService).syncConversation(any());
        verify(feePayment, never()).refund(any(), any(), any(), anyDouble(), anyString());
        // Lệ phí vào doanh thu của người tổ chức để họ rút về ngân hàng.
        UUID organizerId = tournamentRepository.findById(tournamentId).orElseThrow().getOrganizerId();
        verify(feePayment).assignPayee(any(), eq(organizerId));
    }

    @Test
    @DisplayName("hạn 24 giờ có thể qua ngày đóng đăng ký: tiền về trước khi xếp lịch vẫn được xác nhận")
    void paymentAfterCloseDateButBeforeFixturesIsAccepted() throws ServiceException {
        tournament(TournamentStatus.PUBLISHED, LocalDate.now().minusDays(1));

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("tiền về sau khi giải đã xếp lịch: từ chối và tự gửi yêu cầu hoàn phí")
    void latePaymentAfterFixturesIsRefunded() throws ServiceException {
        tournament(TournamentStatus.IN_PROGRESS, LocalDate.now().minusDays(1));
        UUID paymentId = UUID.randomUUID();

        service.apply(paymentId, payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(registration.getStatusReason()).contains("đã gửi yêu cầu hoàn");
        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.REFUND_REQUESTED);
        verify(feePayment).refund(eq(paymentId), eq(registrationId), any(), eq(100_000D), anyString());
    }

    @Test
    @DisplayName("tiền về cho đăng ký đã quá hạn (đã bị hủy) thì hoàn, không mở lại đăng ký")
    void paymentForExpiredRegistrationIsRefunded() throws ServiceException {
        tournament(TournamentStatus.REGISTRATION_OPEN, LocalDate.now().plusDays(2));
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setStatusReason("Quá hạn đóng lệ phí");

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registration.getStatusReason()).startsWith("Quá hạn đóng lệ phí").contains("hoàn");
        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.REFUND_REQUESTED);
    }

    @Test
    @DisplayName("payment-service lỗi khi hoàn: ghi rõ cần hoàn tay, không đánh dấu đã yêu cầu")
    void refundFailureIsRecordedForManualHandling() throws ServiceException {
        tournament(TournamentStatus.CANCELLED, LocalDate.now().plusDays(2));
        when(feePayment.refund(any(), any(), any(), anyDouble(), anyString())).thenReturn(false);

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatusReason()).contains("ban tổ chức sẽ xử lý");
        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("sự kiện thanh toán lặp lại không xác nhận hay hoàn hai lần")
    void duplicateEventIsIgnored() throws ServiceException {
        tournament(TournamentStatus.IN_PROGRESS, LocalDate.now().minusDays(1));
        payment.setStatus(FeePaymentStatus.REFUND_REQUESTED);

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        verify(feePayment, never()).refund(any(), any(), any(), anyDouble(), anyString());
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("thanh toán thất bại không đụng tới trạng thái đăng ký")
    void failedPaymentLeavesRegistrationPending() throws ServiceException {
        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", false, null);

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.FAILED);
    }

    @Test
    @DisplayName("người trả không khớp người đăng ký thì từ chối")
    void payerMustMatchRegistrant() {
        assertThatThrownBy(() -> service.apply(UUID.randomUUID(), UUID.randomUUID(),
                registrationId, 100_000D, "VND", true, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không khớp");
    }

    @Test
    @DisplayName("số tiền lệch lệ phí thì từ chối")
    void amountMismatchIsRejected() {
        assertThatThrownBy(() -> service.apply(UUID.randomUUID(), payerId,
                registrationId, 50_000D, "VND", true, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không khớp lệ phí");
    }
}
