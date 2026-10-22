package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.ClubPhotoResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubPhotoRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.ClubPhoto;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubPhotoServiceImplTest {
    @Mock private ClubRepository clubRepository;
    @Mock private ClubPhotoRepository photoRepository;
    @Mock private ClubMediaStorage mediaStorage;
    @Mock private ClubMemberRepository memberRepository;

    private ClubPhotoServiceImpl service;
    private final UUID clubId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClubPhotoServiceImpl(clubRepository, photoRepository, memberRepository, mediaStorage);
        when(clubRepository.findById(clubId))
                .thenReturn(Optional.of(Club.builder().clubId(clubId).ownerId(ownerId).build()));
    }

    @Test
    void ownerCanFinalizeAndPersistGalleryPhotos() throws Exception {
        String temporaryKey = "users/" + ownerId + "/temp/clubs/gallery/training.webp";
        String publicUrl = "https://cdn.goatsports.test/clubs/gallery/training.webp";
        when(mediaStorage.finalizeUploads(List.of(temporaryKey), "club-gallery:" + clubId + ":"
                + Integer.toUnsignedString(temporaryKey.hashCode()))).thenReturn(List.of(publicUrl));
        when(photoRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ClubPhoto> photos = invocation.getArgument(0);
            photos.forEach(photo -> photo.setPhotoId(UUID.randomUUID()));
            return photos;
        });

        List<ClubPhotoResponseCommand> result = service.addPhotos(clubId, ownerId, List.of(temporaryKey));

        assertThat(result).singleElement().satisfies(photo -> {
            assertThat(photo.getClubId()).isEqualTo(clubId);
            assertThat(photo.getUploadedByUserId()).isEqualTo(ownerId);
            assertThat(photo.getImageUrl()).isEqualTo(publicUrl);
        });
    }

    @Test
    void tenPhotosFinalizeInOneStorageRoundTrip() throws Exception {
        List<String> keys = IntStream.range(0, 10)
                .mapToObj(index -> "temp/clubs/" + ownerId + "/photo-" + index + ".webp")
                .toList();
        List<String> urls = keys.stream().map(key -> "https://cdn.goatsports.test/" + key).toList();
        when(mediaStorage.finalizeUploads(anyList(), anyString())).thenReturn(urls);
        when(photoRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ClubPhotoResponseCommand> result = service.addPhotos(clubId, ownerId, keys);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.captor();
        verify(mediaStorage, times(1)).finalizeUploads(captor.capture(), anyString());
        assertThat(captor.getValue()).containsExactlyElementsOf(keys);
        assertThat(result).hasSize(10);
        verify(photoRepository, times(1)).saveAll(anyList());
    }

    @Test
    void nonOwnerCannotUpload() {
        assertThatThrownBy(() -> service.addPhotos(clubId, UUID.randomUUID(), List.of("temp/photo.webp")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("chủ hoặc quản lý");
        verify(photoRepository, never()).saveAll(anyList());
    }

    @Test
    void permanentOrExternalUrlsAreRejected() throws Exception {
        assertThatThrownBy(() -> service.addPhotos(clubId, ownerId,
                List.of("https://evil.example/photo.webp")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không hợp lệ");
        verify(mediaStorage, never()).finalizeUploads(anyList(), anyString());
    }

    @Test
    void photosAreReturnedAsARequestedPage() throws Exception {
        PageRequest pageable = PageRequest.of(1, 12);
        ClubPhoto photo = ClubPhoto.builder()
                .photoId(UUID.randomUUID())
                .clubId(clubId)
                .imageUrl("https://cdn.goatsports.test/clubs/gallery/photo.webp")
                .uploadedByUserId(ownerId)
                .build();
        when(photoRepository.findByClubId(clubId, pageable))
                .thenReturn(new PageImpl<>(List.of(photo), pageable, 13));

        Page<ClubPhotoResponseCommand> result = service.getPhotos(clubId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(13);
        assertThat(result.getContent()).singleElement()
                .satisfies(item -> assertThat(item.getPhotoId()).isEqualTo(photo.getPhotoId()));
    }

    @Test
    void ownerCanDeletePhotoFromStorageAndGallery() throws Exception {
        UUID photoId = UUID.randomUUID();
        String publicUrl = "https://cdn.goatsports.test/clubs/gallery/photo.webp";
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(ClubPhoto.builder()
                .photoId(photoId)
                .clubId(clubId)
                .imageUrl(publicUrl)
                .uploadedByUserId(ownerId)
                .build()));

        service.deletePhoto(clubId, photoId, ownerId);

        verify(mediaStorage).deleteByUrl(publicUrl);
        verify(photoRepository).deleteById(photoId);
    }

    @Test
    void nonOwnerCannotDeletePhoto() throws Exception {
        assertThatThrownBy(() -> service.deletePhoto(clubId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("chủ hoặc quản lý");
        verify(mediaStorage, never()).deleteByUrl(anyString());
    }

    private void memberIs(UUID userId, ClubRole role, ClubMemberStatus status) {
        when(memberRepository.findByClubIdAndUserId(clubId, userId)).thenReturn(Optional.of(
                ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId).userId(userId)
                        .role(role).status(status).build()));
    }

    @Test
    void activeManagerCanUploadGalleryPhotos() throws Exception {
        UUID managerId = UUID.randomUUID();
        memberIs(managerId, ClubRole.ADMIN, ClubMemberStatus.ACTIVE);
        String temporaryKey = "temp/clubs/gallery/manager-upload.webp";
        when(mediaStorage.finalizeUploads(anyList(), anyString()))
                .thenReturn(List.of("https://cdn.goatsports.test/clubs/gallery/manager-upload.webp"));
        when(photoRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ClubPhotoResponseCommand> result = service.addPhotos(clubId, managerId, List.of(temporaryKey));

        assertThat(result).hasSize(1);
    }

    @Test
    void activeManagerCanDeleteGalleryPhotos() throws Exception {
        UUID managerId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        memberIs(managerId, ClubRole.ADMIN, ClubMemberStatus.ACTIVE);
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(
                ClubPhoto.builder().photoId(photoId).clubId(clubId)
                        .imageUrl("https://cdn.goatsports.test/clubs/gallery/old.webp").build()));

        service.deletePhoto(clubId, photoId, managerId);

        verify(photoRepository).deleteById(photoId);
    }

    @Test
    void plainMemberStillCannotTouchTheGallery() {
        UUID memberId = UUID.randomUUID();
        memberIs(memberId, ClubRole.MEMBER, ClubMemberStatus.ACTIVE);

        assertThatThrownBy(() -> service.addPhotos(clubId, memberId, List.of("temp/clubs/gallery/a.webp")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("chủ hoặc quản lý");
        verify(photoRepository, never()).saveAll(anyList());
    }

    @Test
    void managerWhoLeftTheClubLosesGalleryAccess() {
        UUID formerManagerId = UUID.randomUUID();
        memberIs(formerManagerId, ClubRole.ADMIN, ClubMemberStatus.LEFT);

        assertThatThrownBy(() -> service.addPhotos(clubId, formerManagerId, List.of("temp/clubs/gallery/a.webp")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("chủ hoặc quản lý");
    }
}
