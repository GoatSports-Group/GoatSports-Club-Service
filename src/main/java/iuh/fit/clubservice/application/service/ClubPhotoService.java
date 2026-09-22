package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.response.ClubPhotoResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;

public interface ClubPhotoService {
    List<ClubPhotoResponseCommand> getPhotos(UUID clubId) throws ServiceException;

    List<ClubPhotoResponseCommand> addPhotos(UUID clubId, UUID actorUserId, List<String> imageKeys)
            throws ServiceException;
}
