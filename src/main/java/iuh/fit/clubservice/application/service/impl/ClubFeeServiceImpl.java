package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.CreateClubFeeCommand;
import iuh.fit.clubservice.application.dto.response.ClubFeePaymentResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubFeeResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.ClubFeePaymentRepository;
import iuh.fit.clubservice.application.port.persistence.ClubFeeRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.service.ClubFeeService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubFee;
import iuh.fit.clubservice.domain.entity.ClubFeePayment;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubFeeServiceImpl implements ClubFeeService {
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubFeeRepository feeRepository;
    private final ClubFeePaymentRepository feePaymentRepository;

    @Override
    @Transactional
    public ClubFeeResponseCommand createFee(CreateClubFeeCommand command) throws ServiceException {
        command.validate();
        Club club = requireClub(command.getClubId());
        assertManager(club, command.getActorUserId());
        ClubFee fee = feeRepository.save(ClubFee.builder()
                .clubId(club.getClubId())
                .name(command.getName().trim())
                .amount(command.getAmount())
                .dueDate(command.getDueDate())
                .required(command.getRequired() == null || command.getRequired())
                .build());
        long memberCount = memberRepository.countByClubIdAndStatus(club.getClubId(), ClubMemberStatus.ACTIVE);
        return toResponse(fee, 0L, memberCount, null);
    }

    @Override
    public List<ClubFeeResponseCommand> getClubFees(UUID clubId, UUID requesterId) throws ServiceException {
        Club club = requireClub(clubId);
        ClubMember membership = memberRepository.findByClubIdAndUserId(clubId, requesterId).orElse(null);
        if (membership == null || membership.getStatus() != ClubMemberStatus.ACTIVE) {
            assertManager(club, requesterId);
        }
        long memberCount = memberRepository.countByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);
        UUID membershipId = membership == null ? null : membership.getMembershipId();
        return feeRepository.findByClubId(clubId).stream().map(fee -> {
            List<ClubFeePayment> payments = feePaymentRepository.findByFeeId(fee.getFeeId());
            long paid = payments.stream().filter(this::settled).count();
            ClubFeePayment mine = membershipId == null ? null : payments.stream()
                    .filter(item -> item.getMembershipId().equals(membershipId)).findFirst().orElse(null);
            return toResponse(fee, paid, memberCount, mine);
        }).toList();
    }

    @Override
    public List<ClubFeePaymentResponseCommand> getFeePayments(UUID clubId, UUID feeId, UUID actorUserId)
            throws ServiceException {
        Club club = requireClub(clubId);
        assertManager(club, actorUserId);
        ClubFee fee = requireFee(clubId, feeId);
        Map<UUID, ClubMember> members = memberRepository.findByClubId(clubId).stream()
                .collect(Collectors.toMap(ClubMember::getMembershipId, Function.identity(), (a, b) -> a));
        return feePaymentRepository.findByFeeId(fee.getFeeId()).stream()
                .map(payment -> toResponse(payment, members.get(payment.getMembershipId())))
                .toList();
    }

    @Override
    @Transactional
    public ClubFeePaymentResponseCommand waiveFee(UUID clubId, UUID feeId, UUID membershipId, UUID actorUserId)
            throws ServiceException {
        Club club = requireClub(clubId);
        assertManager(club, actorUserId);
        ClubFee fee = requireFee(clubId, feeId);
        ClubMember member = memberRepository.findById(membershipId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy thành viên của câu lạc bộ."));

        ClubFeePayment payment = feePaymentRepository.findByFeeIdAndMembershipId(fee.getFeeId(), membershipId)
                .orElseGet(() -> ClubFeePayment.builder()
                        .feeId(fee.getFeeId()).membershipId(membershipId).amount(fee.getAmount()).build());
        if (payment.getStatus() == FeePaymentStatus.SUCCEEDED) {
            throw new ServiceException("Thành viên đã đóng khoản phí này.");
        }
        payment.setStatus(FeePaymentStatus.WAIVED);
        payment.setPaidAt(LocalDateTime.now());
        return toResponse(feePaymentRepository.save(payment), member);
    }

    @Override
    @Transactional
    public ClubFeePaymentResponseCommand initiatePayment(UUID clubId, UUID feeId, UUID userId)
            throws ServiceException {
        requireClub(clubId);
        ClubFee fee = requireFee(clubId, feeId);
        ClubMember member = memberRepository.findByClubIdAndUserId(clubId, userId)
                .filter(item -> item.getStatus() == ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new ServiceException("Bạn không phải thành viên đang hoạt động của câu lạc bộ."));

        ClubFeePayment payment = feePaymentRepository
                .findByFeeIdAndMembershipId(fee.getFeeId(), member.getMembershipId())
                .orElseGet(() -> ClubFeePayment.builder()
                        .feeId(fee.getFeeId()).membershipId(member.getMembershipId())
                        .amount(fee.getAmount()).build());
        if (settled(payment)) throw new ServiceException("Khoản phí này đã được xử lý.");
        payment.setAmount(fee.getAmount());
        payment.setStatus(FeePaymentStatus.PENDING);
        return toResponse(feePaymentRepository.save(payment), member);
    }

    @Override
    @Transactional
    public void applyPayment(UUID paymentId, UUID payerId, UUID feePaymentId, Double amount, String currency,
                             boolean succeeded, LocalDateTime paidAt) throws ServiceException {
        if (!"VND".equalsIgnoreCase(currency)) {
            throw new ServiceException("Đơn vị tiền tệ của lệ phí câu lạc bộ không hợp lệ.");
        }
        ClubFeePayment payment = feePaymentRepository.findById(feePaymentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy khoản phí của giao dịch."));
        ClubMember member = memberRepository.findById(payment.getMembershipId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy thành viên của khoản phí."));
        if (!member.getUserId().equals(payerId)) {
            throw new ServiceException("Người thanh toán không khớp thành viên của khoản phí.");
        }
        if (Math.abs(payment.getAmount() - amount) > 0.001D) {
            throw new ServiceException("Số tiền thanh toán không khớp khoản phí câu lạc bộ.");
        }
        payment.setPaymentId(paymentId);
        payment.setStatus(succeeded ? FeePaymentStatus.SUCCEEDED : FeePaymentStatus.FAILED);
        payment.setPaidAt(succeeded ? (paidAt == null ? LocalDateTime.now() : paidAt) : null);
        feePaymentRepository.save(payment);
    }

    private boolean settled(ClubFeePayment payment) {
        return payment.getStatus() == FeePaymentStatus.SUCCEEDED
                || payment.getStatus() == FeePaymentStatus.WAIVED;
    }

    private Club requireClub(UUID clubId) throws ServiceException {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
    }

    private ClubFee requireFee(UUID clubId, UUID feeId) throws ServiceException {
        return feeRepository.findById(feeId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy khoản phí của câu lạc bộ."));
    }

    private void assertManager(Club club, UUID userId) throws ServiceException {
        if (club.getOwnerId().equals(userId)) return;
        boolean manager = memberRepository.findByClubIdAndUserId(club.getClubId(), userId)
                .filter(item -> item.getStatus() == ClubMemberStatus.ACTIVE)
                .map(item -> item.getRole() == ClubRole.OWNER || item.getRole() == ClubRole.ADMIN)
                .orElse(false);
        if (!manager) throw new ServiceException("Bạn không có quyền quản lý khoản phí của câu lạc bộ này.");
    }

    private ClubFeeResponseCommand toResponse(ClubFee fee, long paidCount, long memberCount, ClubFeePayment mine) {
        return ClubFeeResponseCommand.builder()
                .feeId(fee.getFeeId()).clubId(fee.getClubId()).name(fee.getName())
                .amount(fee.getAmount()).dueDate(fee.getDueDate()).required(fee.getRequired())
                .paidCount(paidCount).memberCount(memberCount)
                .myStatus(mine == null ? null : mine.getStatus())
                .myPaidAt(mine == null ? null : mine.getPaidAt())
                .build();
    }

    private ClubFeePaymentResponseCommand toResponse(ClubFeePayment payment, ClubMember member) {
        return ClubFeePaymentResponseCommand.builder()
                .feePaymentId(payment.getFeePaymentId()).feeId(payment.getFeeId())
                .membershipId(payment.getMembershipId())
                .userId(member == null ? null : member.getUserId())
                .amount(payment.getAmount()).paymentId(payment.getPaymentId())
                .status(payment.getStatus()).paidAt(payment.getPaidAt())
                .build();
    }
}
