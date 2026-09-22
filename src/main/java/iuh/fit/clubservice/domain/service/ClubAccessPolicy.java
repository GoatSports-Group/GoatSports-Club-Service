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
}
