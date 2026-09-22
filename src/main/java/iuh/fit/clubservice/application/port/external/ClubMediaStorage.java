package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;

public interface ClubMediaStorage {
    String finalizeUpload(String value, String idempotencyKey) throws ServiceException;

    List<String> finalizeUploads(List<String> values, String idempotencyKey) throws ServiceException;
}
