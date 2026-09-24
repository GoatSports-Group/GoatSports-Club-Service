package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.CreateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubActivityCommand;
import iuh.fit.clubservice.application.dto.response.ClubActivityResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.application.port.persistence.ClubActivityRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.service.ClubActivityService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubActivityServiceImpl implements ClubActivityService {
    private final ClubSupport support;
    private final ClubActivityRepository activityRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubEventPublisher eventPublisher;

    @Override
    public List<ClubActivityResponseCommand> getClubActivities(UUID clubId) {
        return activityRepository.findByClubId(clubId).stream().map(ClubActivityServiceImpl::toResponse).toList();
    }

    @Override
    public Page<ClubActivityResponseCommand> getClubActivitiesPage(UUID clubId, Pageable pageable) {
        return activityRepository.findUpcomingByClubId(clubId, LocalDateTime.now(), pageable)
                .map(ClubActivityServiceImpl::toResponse);
    }

    @Override
    public List<ClubActivityResponseCommand> getMyUpcomingActivities(UUID userId, int limit) {
        // Lấy từ bây giờ trở đi: hoạt động đã qua không còn ý nghĩa ở màn hình "sắp tới".
        return activityRepository.findUpcomingByClubIds(activeClubIdsForUser(userId), LocalDateTime.now()).stream()
                .limit(limit <= 0 ? 10 : limit)
                .map(ClubActivityServiceImpl::toResponse)
                .toList();
    }

    @Override
    public Page<ClubActivityResponseCommand> getMyUpcomingActivitiesPage(UUID userId, Pageable pageable) {
        return activityRepository.findUpcomingByClubIds(activeClubIdsForUser(userId), LocalDateTime.now(), pageable)
                .map(ClubActivityServiceImpl::toResponse);
    }

    @Override
    @Transactional
    public ClubActivityResponseCommand createClubActivity(CreateClubActivityCommand command) throws ServiceException {
        command.validate();
        Club club = support.requireActiveClub(command.getClubId());
        support.assertManager(club, command.getCreatorId());
        ClubActivity activity = activityRepository.save(ClubActivity.builder()
                .clubId(command.getClubId())
                .title(command.getTitle().trim())
                .description(command.getDescription())
                .venueId(command.getVenueId())
                .startAt(command.getStartAt())
                .endAt(command.getEndAt())
                .createdByUserId(command.getCreatorId())
                .build());
        // Người tạo không cần tự báo cho mình.
        List<UUID> receivers = support.activeMemberIds(club.getClubId()).stream()
                .filter(userId -> !userId.equals(command.getCreatorId())).toList();
        eventPublisher.activityCreated(club, activity, receivers);
        return toResponse(activity);
    }

    @Override
    @Transactional
    public ClubActivityResponseCommand updateClubActivity(UpdateClubActivityCommand command) throws ServiceException {
        command.validate();
        Club club = support.requireActiveClub(command.getClubId());
        support.assertManager(club, command.getActorUserId());
        ClubActivity activity = requireActivity(command.getClubId(), command.getActivityId());
        activity.setTitle(command.getTitle().trim());
        activity.setDescription(command.getDescription());
        activity.setVenueId(command.getVenueId());
        activity.setStartAt(command.getStartAt());
        activity.setEndAt(command.getEndAt());
        return toResponse(activityRepository.save(activity));
    }

    @Override
    @Transactional
    public void deleteClubActivity(UUID clubId, UUID activityId, UUID actorUserId) throws ServiceException {
        Club club = support.requireActiveClub(clubId);
        support.assertManager(club, actorUserId);
        requireActivity(clubId, activityId);
        activityRepository.deleteById(activityId);
    }

    private List<UUID> activeClubIdsForUser(UUID userId) {
        return memberRepository.findByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE).stream()
                .map(ClubMember::getClubId)
                .filter(support::isActiveClub)
                .toList();
    }

    /** Kiểm tra hoạt động thuộc đúng CLB trên đường dẫn — chặn sửa hoạt động của CLB khác qua ID đoán được. */
    private ClubActivity requireActivity(UUID clubId, UUID activityId) throws ServiceException {
        return activityRepository.findById(activityId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy hoạt động của câu lạc bộ."));
    }

    private static ClubActivityResponseCommand toResponse(ClubActivity a) {
        return ClubActivityResponseCommand.builder().activityId(a.getActivityId()).clubId(a.getClubId())
                .title(a.getTitle()).description(a.getDescription()).startAt(a.getStartAt()).endAt(a.getEndAt())
                .venueId(a.getVenueId()).createdBy(a.getCreatedByUserId()).createdAt(a.getCreatedAt()).build();
    }
}
