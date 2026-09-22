package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.ClubPhotoResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubPhotoRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.service.ClubPhotoService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.ClubPhoto;
import iuh.fit.clubservice.domain.service.ClubAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubPhotoServiceImpl implements ClubPhotoService {
    private static final int MAX_BATCH_SIZE = 10;

    private final ClubRepository clubRepository;
    private final ClubPhotoRepository photoRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubMediaStorage mediaStorage;

    @Override
    @Transactional(readOnly = true)
    public Page<ClubPhotoResponseCommand> getPhotos(UUID clubId, Pageable pageable) throws ServiceException {
        requireClub(clubId);
        return photoRepository.findByClubId(clubId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public List<ClubPhotoResponseCommand> addPhotos(UUID clubId, UUID actorUserId, List<String> imageKeys)
            throws ServiceException {
        Club club = requireActiveClub(clubId);
        assertManager(club, actorUserId, "đăng ảnh");
        if (imageKeys == null || imageKeys.isEmpty() || imageKeys.size() > MAX_BATCH_SIZE) {
            throw new ServiceException("Mỗi lần đăng từ 1 đến 10 ảnh.");
        }

        List<String> keys = new ArrayList<>(imageKeys.size());
        for (String value : imageKeys) {
            keys.add(normalizeTemporaryKey(value));
        }
        List<String> publicUrls = mediaStorage.finalizeUploads(keys, batchIdempotencyKey(clubId, keys));

        List<ClubPhoto> photos = new ArrayList<>(publicUrls.size());
        for (String publicUrl : publicUrls) {
            photos.add(ClubPhoto.builder()
                    .clubId(clubId)
                    .imageUrl(publicUrl)
                    .uploadedByUserId(actorUserId)
                    .build());
        }
        return photoRepository.saveAll(photos).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void deletePhoto(UUID clubId, UUID photoId, UUID actorUserId) throws ServiceException {
        Club club = requireActiveClub(clubId);
        assertManager(club, actorUserId, "xóa ảnh");
        ClubPhoto photo = photoRepository.findById(photoId)
                .filter(item -> clubId.equals(item.getClubId()))
                .orElseThrow(() -> new ServiceException("Không tìm thấy ảnh trong câu lạc bộ."));
        mediaStorage.deleteByUrl(photo.getImageUrl());
        photoRepository.deleteById(photoId);
    }

    private void assertManager(Club club, UUID actorUserId, String action) throws ServiceException {
        ClubMember membership = memberRepository.findByClubIdAndUserId(club.getClubId(), actorUserId).orElse(null);
        if (!ClubAccessPolicy.isManager(club, membership, actorUserId)) {
            throw new ServiceException("Chỉ chủ hoặc quản lý câu lạc bộ mới có thể " + action + ".");
        }
    }

    private Club requireClub(UUID clubId) throws ServiceException {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
    }

    private Club requireActiveClub(UUID clubId) throws ServiceException {
        Club club = requireClub(clubId);
        if (club.getDisbandedAt() != null || Boolean.FALSE.equals(club.getActive())) {
            throw new ServiceException("Câu lạc bộ đã giải tán.");
        }
        return club;
    }

    private String batchIdempotencyKey(UUID clubId, List<String> keys) {
        return "club-gallery:" + clubId + ":" + Integer.toUnsignedString(String.join("\n", keys).hashCode());
    }

    private String normalizeTemporaryKey(String value) throws ServiceException {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty() || !(key.startsWith("temp/") || key.contains("/temp/"))) {
            throw new ServiceException("Ảnh tải lên không hợp lệ.");
        }
        return key;
    }

    private ClubPhotoResponseCommand toResponse(ClubPhoto photo) {
        return ClubPhotoResponseCommand.builder()
                .photoId(photo.getPhotoId())
                .clubId(photo.getClubId())
                .imageUrl(photo.getImageUrl())
                .uploadedByUserId(photo.getUploadedByUserId())
                .createdAt(photo.getCreatedAt())
                .build();
    }

}
