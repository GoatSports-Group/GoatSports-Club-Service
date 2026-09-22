package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;

public interface ClubMediaStorage {
    String finalizeUpload(String value, String idempotencyKey) throws ServiceException;
}
