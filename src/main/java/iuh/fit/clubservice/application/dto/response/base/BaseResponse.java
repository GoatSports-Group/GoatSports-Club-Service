package iuh.fit.clubservice.application.dto.response.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BaseResponse<T> {
    private int statusCode;
    private String message;
    private String error;
    private T data;
}
