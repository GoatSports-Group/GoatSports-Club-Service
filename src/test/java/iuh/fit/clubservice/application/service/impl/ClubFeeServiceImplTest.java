package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.CreateClubFeeCommand;
import iuh.fit.clubservice.application.dto.response.ClubFeePaymentResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.ClubFeePaymentRepository;
import iuh.fit.clubservice.application.port.persistence.ClubFeeRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubFee;
import iuh.fit.clubservice.domain.entity.ClubFeePayment;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubFeeServiceImplTest {

    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository memberRepository;
    @Mock private ClubFeeRepository feeRepository;
    @Mock private ClubFeePaymentRepository feePaymentRepository;

    private ClubFeeServiceImpl service;

    private final UUID clubId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID memberUserId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID feeId = UUID.randomUUID();
    private final UUID feePaymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClubFeeServiceImpl(clubRepository, memberRepository, feeRepository, feePaymentRepository);

        Club club = Club.builder().clubId(clubId).ownerId(ownerId).name("CLB test")
                .sportType(SportType.FOOTBALL).active(true).build();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(feeRepository.findById(feeId)).thenReturn(Optional.of(ClubFee.builder()
                .feeId(feeId).clubId(clubId).name("Phí tháng 9").amount(200_000D)
                .dueDate(LocalDate.now().plusDays(7)).required(true).build()));
        when(feeRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(feePaymentRepository.save(any())).thenAnswer(call -> {
            ClubFeePayment payment = call.getArgument(0);
            if (payment.getFeePaymentId() == null) payment.setFeePaymentId(feePaymentId);
            return payment;
        });
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(activeMember()));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId))
                .thenReturn(Optional.of(activeMember()));
    }

    private ClubMember activeMember() {
        return ClubMember.builder().membershipId(membershipId).clubId(clubId).userId(memberUserId)
                .role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).joinedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("thành viên thường không tạo được khoản phí")
    void plainMemberCannotCreateFee() {
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId))
                .thenReturn(Optional.of(activeMember()));

        assertThatThrownBy(() -> service.createFee(CreateClubFeeCommand.builder()
                .clubId(clubId).actorUserId(memberUserId).name("Phí")
                .amount(100_000D).dueDate(LocalDate.now().plusDays(3)).build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không có quyền");

        verify(feeRepository, never()).save(any());
    }

    @Test
    @DisplayName("chủ câu lạc bộ tạo được khoản phí")
    void ownerCreatesFee() throws ServiceException {
        when(memberRepository.countByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(12L);

        var response = service.createFee(CreateClubFeeCommand.builder()
                .clubId(clubId).actorUserId(ownerId).name("  Phí tháng 10  ")
                .amount(150_000D).dueDate(LocalDate.now().plusDays(10)).build());

        assertThat(response.getName()).isEqualTo("Phí tháng 10");
        assertThat(response.getMemberCount()).isEqualTo(12L);
        assertThat(response.getPaidCount()).isZero();
        assertThat(response.getRequired()).isTrue();
    }

    @Test
    @DisplayName("người ngoài câu lạc bộ không khởi tạo được khoản đóng phí")
    void outsiderCannotInitiatePayment() {
        UUID outsider = UUID.randomUUID();
        when(memberRepository.findByClubIdAndUserId(clubId, outsider)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiatePayment(clubId, feeId, outsider))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không phải thành viên");
    }

    @Test
    @DisplayName("khởi tạo đóng phí tạo bản ghi PENDING đúng số tiền của khoản phí")
    void initiatePaymentCreatesPendingRecord() throws ServiceException {
        when(feePaymentRepository.findByFeeIdAndMembershipId(feeId, membershipId)).thenReturn(Optional.empty());

        ClubFeePaymentResponseCommand response = service.initiatePayment(clubId, feeId, memberUserId);

        assertThat(response.getStatus()).isEqualTo(FeePaymentStatus.PENDING);
        assertThat(response.getAmount()).isEqualTo(200_000D);
        assertThat(response.getMembershipId()).isEqualTo(membershipId);
    }

    @Test
    @DisplayName("khoản phí đã đóng thì không khởi tạo lại")
    void settledFeeCannotBePaidAgain() {
        when(feePaymentRepository.findByFeeIdAndMembershipId(feeId, membershipId))
                .thenReturn(Optional.of(ClubFeePayment.builder()
                        .feePaymentId(feePaymentId).feeId(feeId).membershipId(membershipId)
                        .amount(200_000D).status(FeePaymentStatus.SUCCEEDED).build()));

        assertThatThrownBy(() -> service.initiatePayment(clubId, feeId, memberUserId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã được xử lý");
    }

    @Test
    @DisplayName("quản lý miễn phí cho thành viên chưa đóng")
    void managerWaivesUnpaidFee() throws ServiceException {
        when(feePaymentRepository.findByFeeIdAndMembershipId(feeId, membershipId)).thenReturn(Optional.empty());

        ClubFeePaymentResponseCommand response = service.waiveFee(clubId, feeId, membershipId, ownerId);

        assertThat(response.getStatus()).isEqualTo(FeePaymentStatus.WAIVED);
        assertThat(response.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("không miễn được khoản phí thành viên đã đóng")
    void cannotWaiveAlreadyPaidFee() {
        when(feePaymentRepository.findByFeeIdAndMembershipId(feeId, membershipId))
                .thenReturn(Optional.of(ClubFeePayment.builder()
                        .feePaymentId(feePaymentId).feeId(feeId).membershipId(membershipId)
                        .amount(200_000D).status(FeePaymentStatus.SUCCEEDED).build()));

        assertThatThrownBy(() -> service.waiveFee(clubId, feeId, membershipId, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã đóng");
    }

    @Test
    @DisplayName("thanh toán thành công chuyển khoản phí sang SUCCEEDED")
    void successfulPaymentSettlesFee() throws ServiceException {
        ClubFeePayment payment = ClubFeePayment.builder()
                .feePaymentId(feePaymentId).feeId(feeId).membershipId(membershipId)
                .amount(200_000D).status(FeePaymentStatus.PENDING).build();
        when(feePaymentRepository.findById(feePaymentId)).thenReturn(Optional.of(payment));
        UUID paymentId = UUID.randomUUID();
        LocalDateTime paidAt = LocalDateTime.now();

        service.applyPayment(paymentId, memberUserId, feePaymentId, 200_000D, "VND", true, paidAt);

        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.SUCCEEDED);
        assertThat(payment.getPaymentId()).isEqualTo(paymentId);
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("người trả không khớp thành viên thì từ chối")
    void payerMustMatchMember() {
        when(feePaymentRepository.findById(feePaymentId)).thenReturn(Optional.of(ClubFeePayment.builder()
                .feePaymentId(feePaymentId).feeId(feeId).membershipId(membershipId)
                .amount(200_000D).status(FeePaymentStatus.PENDING).build()));

        assertThatThrownBy(() -> service.applyPayment(UUID.randomUUID(), UUID.randomUUID(),
                feePaymentId, 200_000D, "VND", true, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không khớp thành viên");
    }

    @Test
    @DisplayName("số tiền lệch với khoản phí thì từ chối")
    void amountMismatchIsRejected() {
        when(feePaymentRepository.findById(feePaymentId)).thenReturn(Optional.of(ClubFeePayment.builder()
                .feePaymentId(feePaymentId).feeId(feeId).membershipId(membershipId)
                .amount(200_000D).status(FeePaymentStatus.PENDING).build()));

        assertThatThrownBy(() -> service.applyPayment(UUID.randomUUID(), memberUserId,
                feePaymentId, 50_000D, "VND", true, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không khớp khoản phí");
    }

    @Test
    @DisplayName("thanh toán thất bại không đánh dấu đã đóng")
    void failedPaymentDoesNotSettleFee() throws ServiceException {
        ClubFeePayment payment = ClubFeePayment.builder()
                .feePaymentId(feePaymentId).feeId(feeId).membershipId(membershipId)
                .amount(200_000D).status(FeePaymentStatus.PENDING).build();
        when(feePaymentRepository.findById(feePaymentId)).thenReturn(Optional.of(payment));

        service.applyPayment(UUID.randomUUID(), memberUserId, feePaymentId, 200_000D, "VND", false, null);

        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.FAILED);
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("thành viên thấy trạng thái khoản phí của chính mình")
    void memberSeesOwnFeeStatus() throws ServiceException {
        when(memberRepository.countByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(3L);
        when(feeRepository.findByClubId(clubId)).thenReturn(List.of(ClubFee.builder()
                .feeId(feeId).clubId(clubId).name("Phí tháng 9").amount(200_000D)
                .dueDate(LocalDate.now().plusDays(7)).required(true).build()));
        when(feePaymentRepository.findByFeeId(feeId)).thenReturn(List.of(
                ClubFeePayment.builder().feePaymentId(feePaymentId).feeId(feeId)
                        .membershipId(membershipId).amount(200_000D)
                        .status(FeePaymentStatus.SUCCEEDED).build(),
                ClubFeePayment.builder().feePaymentId(UUID.randomUUID()).feeId(feeId)
                        .membershipId(UUID.randomUUID()).amount(200_000D)
                        .status(FeePaymentStatus.PENDING).build()));

        var fees = service.getClubFees(clubId, memberUserId);

        assertThat(fees).hasSize(1);
        assertThat(fees.get(0).getMyStatus()).isEqualTo(FeePaymentStatus.SUCCEEDED);
        assertThat(fees.get(0).getPaidCount()).isEqualTo(1L);
        assertThat(fees.get(0).getMemberCount()).isEqualTo(3L);
    }
}
