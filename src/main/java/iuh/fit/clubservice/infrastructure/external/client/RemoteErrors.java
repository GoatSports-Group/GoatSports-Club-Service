package iuh.fit.clubservice.infrastructure.external.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

final class RemoteErrors {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RemoteErrors() { }

    static String message(FeignException exception, String fallback) {
        try {
            JsonNode body = MAPPER.readTree(exception.contentUTF8());
            JsonNode message = body.path("message");
            if (message.isTextual() && !message.asText().isBlank()) return message.asText();
            JsonNode error = body.path("error");
            if (error.isTextual() && !error.asText().isBlank()) return error.asText();
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
