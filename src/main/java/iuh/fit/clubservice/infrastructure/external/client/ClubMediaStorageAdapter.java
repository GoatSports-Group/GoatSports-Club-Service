package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@RequiredArgsConstructor
public class ClubMediaStorageAdapter implements ClubMediaStorage {
    private final StorageClient storageClient;

    @Override
    public String finalizeUpload(String value, String idempotencyKey) throws ServiceException {
        return finalizeUploads(Collections.singletonList(value), idempotencyKey).getFirst();
    }

    @Override
    public List<String> finalizeUploads(List<String> values, String idempotencyKey) throws ServiceException {
        List<String> resolved = new ArrayList<>(values.size());
        List<StorageClient.FileMoveRequest> temporaryFiles = new ArrayList<>(values.size());
        for (String value : values) {
            String normalized = value == null ? null : value.trim();
            if (normalized != null && normalized.isEmpty()) {
                normalized = null;
            }
            resolved.add(normalized);
            if (normalized != null && isTemporaryKey(normalized)) {
                temporaryFiles.add(new StorageClient.FileMoveRequest(normalized));
            }
        }
        if (temporaryFiles.isEmpty()) {
            return resolved;
        }

        BaseResponse<List<String>> response = storageClient.confirmFiles(temporaryFiles);
        List<String> permanentKeys = response == null ? null : response.getData();
        if (permanentKeys == null || permanentKeys.size() != temporaryFiles.size()
                || permanentKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new ServiceException("Không thể xác nhận ảnh câu lạc bộ đã tải lên.");
        }

        storageClient.moveFiles(new StorageClient.MoveFileRequest(temporaryFiles, idempotencyKey));

        List<String> publicUrls = resolveUrls(permanentKeys);
        int cursor = 0;
        for (int index = 0; index < resolved.size(); index++) {
            String value = resolved.get(index);
            if (value != null && isTemporaryKey(value)) {
                resolved.set(index, publicUrls.get(cursor++));
            }
        }
        return resolved;
    }

    @Override
    public void deleteByUrl(String publicUrl) throws ServiceException {
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new ServiceException("Đường dẫn ảnh câu lạc bộ không hợp lệ.");
        }
        try {
            storageClient.deleteFileByUrl(publicUrl.trim());
        } catch (RuntimeException exception) {
            throw new ServiceException("Không thể xóa ảnh câu lạc bộ khỏi kho lưu trữ.");
        }
    }

    private List<String> resolveUrls(List<String> keys) throws ServiceException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> pendingUrls = keys.stream()
                    .map(key -> executor.submit(() -> storageClient.getFileUrl(key)))
                    .toList();
            List<String> urls = new ArrayList<>(keys.size());
            for (Future<String> pendingUrl : pendingUrls) {
                String url = pendingUrl.get();
                if (url == null || url.isBlank()) {
                    throw new ServiceException("Không thể lấy đường dẫn ảnh câu lạc bộ.");
                }
                urls.add(url);
            }
            return urls;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Không thể lấy đường dẫn ảnh câu lạc bộ.");
        } catch (ExecutionException exception) {
            throw new ServiceException("Không thể lấy đường dẫn ảnh câu lạc bộ.");
        }
    }

    private boolean isTemporaryKey(String value) {
        return value.startsWith("temp/") || value.contains("/temp/");
    }
}
