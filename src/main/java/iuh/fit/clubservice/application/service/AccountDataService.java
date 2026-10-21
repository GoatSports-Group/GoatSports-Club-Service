package iuh.fit.clubservice.application.service;

import java.util.List;
import java.util.UUID;

/** Phần của club-service trong luồng xóa tài khoản (auth-service điều phối). */
public interface AccountDataService {
    /** Mã các việc còn dang dở ở club-service chặn việc xóa tài khoản. */
    List<String> deletionBlockers(UUID userId);

    /** Tài khoản đã bị ẩn danh hóa: rời mọi CLB, hủy lời mời đang chờ. Gọi lại nhiều lần vẫn an toàn. */
    void eraseUserData(UUID userId);
}
