package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.dto.response.ClubPhotoResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.service.ClubPhotoService;
import iuh.fit.clubservice.presentation.annotation.ApiMessage;
import iuh.fit.clubservice.presentation.request.AddClubPhotosRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clubs/{clubId}/photos")
@RequiredArgsConstructor
public class ClubPhotoController {
    private final ClubPhotoService photoService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @ApiMessage("Lấy thư viện ảnh câu lạc bộ thành công")
    public List<ClubPhotoResponseCommand> getPhotos(@PathVariable UUID clubId) throws ServiceException {
        return photoService.getPhotos(clubId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Đăng ảnh câu lạc bộ thành công")
    public List<ClubPhotoResponseCommand> addPhotos(@PathVariable UUID clubId,
            @Valid @RequestBody AddClubPhotosRequest request) throws ServiceException {
        UUID actorUserId = currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Bạn cần đăng nhập để đăng ảnh câu lạc bộ."));
        return photoService.addPhotos(clubId, actorUserId, request.getImageKeys());
    }
}
