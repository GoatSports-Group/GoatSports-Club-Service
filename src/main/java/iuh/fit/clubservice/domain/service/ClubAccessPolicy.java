package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;

import java.util.UUID;

/**
 * Mot noi duy nhat dinh nghia "ai la quan ly CLB".
 * Truoc day luat nay nam rai o tung service, nen noi long o cho nay ma quen cho kia
 * se tao ra lo hong quyen im lang.
 */
public final class ClubAccessPolicy {

    private ClubAccessPolicy() { }

    public static boolean isManager(Club club, ClubMember membership, UUID userId) {
        if (club == null || userId == null) return false;
        if (userId.equals(club.getOwnerId())) return true;
        return membership != null
                && membership.getStatus() == ClubMemberStatus.ACTIVE
                && membership.getRole() == ClubRole.ADMIN;
    }

    /**
     * Quan ly chi xoa/cam duoc thanh vien thuong; xu ly mot quan ly khac la viec cua chu CLB. Cung ly do voi viec chi
     * chu CLB doi vai tro: quan ly ngang cap khong duoc tu xu ly nhau. Goi sau khi da kiem nguoi thao tac la quan ly.
     */
    public static boolean canDiscipline(Club club, ClubMember target, UUID actorUserId) {
        if (club == null || target == null || actorUserId == null) return false;
        return actorUserId.equals(club.getOwnerId()) || target.getRole() != ClubRole.ADMIN;
    }

    /** Lenh cam do chu CLB dat thi chi chu CLB go; lenh cam cua quan ly thi quan ly nao cung go duoc. */
    public static boolean canLiftBan(Club club, ClubMember banned, UUID actorUserId) {
        if (club == null || banned == null || actorUserId == null) return false;
        return actorUserId.equals(club.getOwnerId()) || !club.getOwnerId().equals(banned.getRespondedBy());
    }
}
