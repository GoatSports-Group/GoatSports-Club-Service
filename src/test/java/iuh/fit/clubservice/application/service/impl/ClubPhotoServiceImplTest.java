package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import iuh.fit.clubservice.application.port.persistence.ClubPhotoRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private ClubPhotoServiceImpl service;
    private final UUID clubId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClubPhotoServiceImpl(clubRepository, photoRepository, mediaStorage);
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

        var result = service.addPhotos(clubId, ownerId, List.of(temporaryKey));

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

        var result = service.addPhotos(clubId, ownerId, keys);

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
                .hasMessageContaining("chủ câu lạc bộ");
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
}
