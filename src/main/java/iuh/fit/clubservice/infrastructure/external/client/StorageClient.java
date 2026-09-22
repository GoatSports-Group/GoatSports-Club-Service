package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.infrastructure.config.StorageClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "GOATSPORTS-STORAGE-SERVICE",
        url = "${storage.service.url}",
        path = "/internal/files",
        configuration = StorageClientConfig.class
)
public interface StorageClient {
    @PostMapping("/confirm")
    BaseResponse<List<String>> confirmFiles(@RequestBody List<FileMoveRequest> requests);

    @PostMapping("/move")
    void moveFiles(@RequestBody MoveFileRequest request);

    @GetMapping
    String getFileUrl(@RequestParam String key);

    record FileMoveRequest(String tempKey) { }
    record MoveFileRequest(List<FileMoveRequest> requests, String idempotencyKey) { }
}
