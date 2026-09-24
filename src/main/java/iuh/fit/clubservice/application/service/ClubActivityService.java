package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.CreateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubActivityCommand;
import iuh.fit.clubservice.application.dto.response.ClubActivityResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Hoạt động (buổi tập, giao lưu) của câu lạc bộ: ban quản trị tạo/sửa/xóa, thành viên xem lịch sắp tới. */
public interface ClubActivityService {

    List<ClubActivityResponseCommand> getClubActivities(UUID clubId);

    Page<ClubActivityResponseCommand> getClubActivitiesPage(UUID clubId, Pageable pageable);

    /** Hoạt động sắp tới của mọi CLB mà người dùng đang là thành viên (bỏ qua CLB đã giải tán). */
    List<ClubActivityResponseCommand> getMyUpcomingActivities(UUID userId, int limit);

    Page<ClubActivityResponseCommand> getMyUpcomingActivitiesPage(UUID userId, Pageable pageable);

    ClubActivityResponseCommand createClubActivity(CreateClubActivityCommand command) throws ServiceException;

    ClubActivityResponseCommand updateClubActivity(UpdateClubActivityCommand command) throws ServiceException;

    void deleteClubActivity(UUID clubId, UUID activityId, UUID actorUserId) throws ServiceException;
}
