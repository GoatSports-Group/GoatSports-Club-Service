package iuh.fit.clubservice.infrastructure.exception;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ServiceException.class)
    ResponseEntity<BaseResponse<Object>> service(ServiceException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), "Yêu cầu không hợp lệ");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<BaseResponse<Object>> validation(MethodArgumentNotValidException exception) {
        Object message = exception.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage).toList();
        return error(HttpStatus.BAD_REQUEST, message, "Dữ liệu không hợp lệ");
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<BaseResponse<Object>> notFound(NoResourceFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), "Đường dẫn không tồn tại");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<BaseResponse<Object>> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống.", "Lỗi hệ thống");
    }
    private ResponseEntity<BaseResponse<Object>> error(HttpStatus status, Object message, String error) {
        BaseResponse<Object> response = new BaseResponse<>();
        response.setStatusCode(status.value()); response.setMessage(String.valueOf(message)); response.setError(error);
        return ResponseEntity.status(status).body(response);
    }
}
