package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ClubMediaStorageAdapter implements ClubMediaStorage {
    private final StorageClient storageClient;

    @Override
    public String finalizeUpload(String value, String idempotencyKey) throws ServiceException {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) return null;
        if (!isTemporaryKey(normalized)) return normalized;

        StorageClient.FileMoveRequest file = new StorageClient.FileMoveRequest(normalized);
        BaseResponse<List<String>> response = storageClient.confirmFiles(List.of(file));
        List<String> permanentKeys = response == null ? null : response.getData();
        if (permanentKeys == null || permanentKeys.size() != 1 || permanentKeys.getFirst().isBlank()) {
            throw new ServiceException("Không thể xác nhận ảnh câu lạc bộ đã tải lên.");
        }

        storageClient.moveFiles(new StorageClient.MoveFileRequest(List.of(file), idempotencyKey));
        String publicUrl = storageClient.getFileUrl(permanentKeys.getFirst());
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new ServiceException("Không thể lấy đường dẫn ảnh câu lạc bộ.");
        }
        return publicUrl;
    }

    private boolean isTemporaryKey(String value) {
        return value.startsWith("temp/") || value.contains("/temp/");
    }
}
