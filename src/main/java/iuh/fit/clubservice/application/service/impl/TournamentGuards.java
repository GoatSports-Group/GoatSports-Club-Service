package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.entity.Tournament;

import java.util.UUID;

/** Điều kiện chặn dùng chung cho các use case điều hành giải (chỉ chủ sân tổ chức giải mới được thao tác). */
final class TournamentGuards {

    private TournamentGuards() {
    }

    static void assertOrganizer(Tournament tournament, UUID userId) throws ServiceException {
        if (!tournament.getOrganizerId().equals(userId)) {
            throw new ServiceException("Chỉ người tổ chức mới có quyền thực hiện thao tác này.");
        }
    }
}
