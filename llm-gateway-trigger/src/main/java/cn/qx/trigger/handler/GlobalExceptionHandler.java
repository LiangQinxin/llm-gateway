package cn.qx.trigger.handler;

import cn.qx.api.response.Response;
import cn.qx.types.enums.ResponseCode;
import cn.qx.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 管理面统一异常映射。
 */
@Slf4j
@RestControllerAdvice(basePackages = {
        "cn.qx.trigger.channel",
        "cn.qx.trigger.identity",
        "cn.qx.trigger.audit"
})
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Response<Void>> handleAppException(AppException exception) {
        log.warn("业务异常: code={}, info={}", exception.getCode(), exception.getInfo());
        return ResponseEntity.status(toHttpStatus(exception.getCode()))
                .body(Response.<Void>builder()
                        .code(exception.getCode())
                        .info(exception.getInfo())
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("参数异常: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(Response.<Void>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(exception.getMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleUnexpected(Exception exception) {
        log.error("系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.<Void>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info(ResponseCode.UN_ERROR.getInfo())
                        .build());
    }

    private static HttpStatus toHttpStatus(String code) {
        return switch (code) {
            case "0401" -> HttpStatus.UNAUTHORIZED;
            case "0403" -> HttpStatus.FORBIDDEN;
            case "0404" -> HttpStatus.NOT_FOUND;
            case "0409" -> HttpStatus.CONFLICT;
            case "0002" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
