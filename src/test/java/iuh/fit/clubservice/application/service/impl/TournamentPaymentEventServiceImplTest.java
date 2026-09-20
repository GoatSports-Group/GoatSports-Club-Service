package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentPaymentEventServiceImplTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentPaymentRepository paymentRepository;
    @Mock private iuh.fit.clubservice.application.port.external.ClubConversationSync conversationSync;

    private TournamentPaymentEventServiceImpl service;

    private final UUID tournamentId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID payerId = UUID.randomUUID();
    private TournamentRegistration registration;

    @BeforeEach
    void setUp() {
        service = new TournamentPaymentEventServiceImpl(
                tournamentRepository, registrationRepository, paymentRepository, conversationSync);

        registration = TournamentRegistration.builder()
                .registrationId(registrationId).tournamentId(tournamentId).registeredBy(payerId)
                .type(RegistrationType.INDIVIDUAL).status(RegistrationStatus.PENDING_PAYMENT).build();

        when(registrationRepository.findById(registrationId)).thenReturn(Optional.of(registration));
        when(registrationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(paymentRepository.findByRegistrationId(registrationId)).thenReturn(Optional.of(
                TournamentPayment.builder().registrationId(registrationId).amount(100_000D)
                        .status(FeePaymentStatus.PENDING).build()));
        when(paymentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private Tournament tournament(TournamentStatus status, LocalDate closeDate) {
        return Tournament.builder()
                .tournamentId(tournamentId).organizerId(UUID.randomUUID()).name("Giải test")
                .sportType(SportType.FOOTBALL).format(TournamentFormat.ROUND_ROBIN).status(status)
                .maxParticipants(8).entryFee(100_000D).prizePool(0D)
                .registrationOpenDate(LocalDate.now().minusDays(5))
                .registrationCloseDate(closeDate)
                .startDate(LocalDate.now().plusDays(5)).endDate(LocalDate.now().plusDays(6))
                .build();
    }

    @Test
    @DisplayName("thanh toán trong hạn thì xác nhận đăng ký")
    void paymentInTimeConfirmsRegistration() throws ServiceException {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(
                tournament(TournamentStatus.REGISTRATION_OPEN, LocalDate.now().plusDays(2))));

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registration.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("tiền về sau khi giải đã bắt đầu thì không tạo đội không có trận nào")
    void latePaymentAfterFixturesIsRejected() throws ServiceException {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(
                tournament(TournamentStatus.IN_PROGRESS, LocalDate.now().plusDays(2))));

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(registration.getStatusReason()).contains("hoàn lệ phí");
        assertThat(registration.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("tiền về sau hạn đăng ký cũng bị từ chối")
    void paymentAfterRegistrationCloseIsRejected() throws ServiceException {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(
                tournament(TournamentStatus.REGISTRATION_CLOSED, LocalDate.now().minusDays(1))));

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
    }

    @Test
    @DisplayName("tiền về cho giải đã hủy thì không xác nhận")
    void paymentForCancelledTournamentIsRejected() throws ServiceException {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(
                tournament(TournamentStatus.CANCELLED, LocalDate.now().plusDays(2))));

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
    }

    @Test
    @DisplayName("thanh toán thất bại không đụng tới trạng thái đăng ký")
    void failedPaymentLeavesRegistrationPending() throws ServiceException {
        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", false, null);

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
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

    @Test
    @DisplayName("đơn vị tiền tệ sai thì từ chối")
    void wrongCurrencyIsRejected() {
        assertThatThrownBy(() -> service.apply(UUID.randomUUID(), payerId,
                registrationId, 100_000D, "USD", true, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("đăng ký đã xác nhận rồi thì callback lặp không làm gì thêm")
    void repeatedCallbackOnConfirmedRegistrationIsNoop() throws ServiceException {
        registration.setStatus(RegistrationStatus.CONFIRMED);

        service.apply(UUID.randomUUID(), payerId, registrationId, 100_000D, "VND", true, LocalDateTime.now());

        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }
}
