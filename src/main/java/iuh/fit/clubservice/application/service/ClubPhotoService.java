package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.response.ClubPhotoResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClubPhotoService {
    Page<ClubPhotoResponseCommand> getPhotos(UUID clubId, Pageable pageable) throws ServiceException;

    List<ClubPhotoResponseCommand> addPhotos(UUID clubId, UUID actorUserId, List<String> imageKeys)
            throws ServiceException;

    void deletePhoto(UUID clubId, UUID photoId, UUID actorUserId) throws ServiceException;
}
