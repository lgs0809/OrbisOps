package cn.lgs.orbisops.trigger.http;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.types.enums.ResponseCode;
import cn.lgs.orbisops.types.exception.AppException;
import cn.lgs.orbisops.types.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<Void>> handleUnsupportedMethod(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        var builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (e.getSupportedHttpMethods() != null) {
            builder.allow(e.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        }
        return builder.body(response(ResponseCode.ILLEGAL_PARAMETER.getCode(), "HTTP_METHOD_NOT_ALLOWED:" + e.getMethod()));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException e) {
        // SSE controllers own their timeout event and completion. Writing a JSON body here would corrupt text/event-stream.
        log.warn("异步请求已超时并由流式 Controller 收敛");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleBadRequest(Exception e) {
        return response(ResponseCode.ILLEGAL_PARAMETER.getCode(), message(e, ResponseCode.ILLEGAL_PARAMETER.getInfo()));
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Response<Void> handleAccessDenied(SecurityException e) {
        return response(ResponseCode.FORBIDDEN.getCode(), message(e, ResponseCode.FORBIDDEN.getInfo()));
    }

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Response<Void> handleBiz(BizException e) {
        return response(value(e.getCode(), ResponseCode.UN_ERROR.getCode()), value(e.getInfo(), ResponseCode.UN_ERROR.getInfo()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Response<Void>> handleIllegalState(IllegalStateException e) {
        String message = message(e, ResponseCode.UN_ERROR.getInfo());
        HttpStatus status = domainStateConflict(message) || containsAny(message,
                "TASK_ACCEPTANCE_",
                "状态不允许", "当前状态不允许", "未处于 APPROVED", "不能落地",
                "READY_FOR_REVIEW", "REVIEWING", "NEEDS_REPLAN",
                "版本", "hash", "冲突", "已取消")
                ? HttpStatus.CONFLICT
                : HttpStatus.INTERNAL_SERVER_ERROR;
        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            log.error("接口处理失败", e);
        }
        return ResponseEntity.status(status)
                .body(response(ResponseCode.UN_ERROR.getCode(), message));
    }

    @ExceptionHandler(AppException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<Void> handleApp(AppException e) {
        return response(value(e.getCode(), ResponseCode.UN_ERROR.getCode()), value(e.getInfo(), ResponseCode.UN_ERROR.getInfo()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<Void> handleThrowable(Exception e) {
        log.error("接口处理失败", e);
        return response(ResponseCode.UN_ERROR.getCode(), message(e, ResponseCode.UN_ERROR.getInfo()));
    }

    private Response<Void> response(String code, String info) {
        return Response.<Void>builder()
                .code(code)
                .info(info)
                .data(null)
                .build();
    }

    private String message(Exception e, String fallback) {
        return value(e == null ? null : e.getMessage(), fallback);
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean containsAny(String message, String... patterns) {
        if (message == null || message.isBlank()) {
            return false;
        }
        for (String pattern : patterns) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean domainStateConflict(String message) {
        // Published domain reason codes are state conflicts; infrastructure faults
        // remain 500. Do not classify every CHANGE_PACKAGE_* error as user input.
        String reason = message.split("[:：]", 2)[0];
        return java.util.Set.of("CHANGE_PACKAGE_TRANSITION_REJECTED", "CHANGE_PACKAGE_APPROVAL_REQUIRED",
                "CHANGE_PACKAGE_APPROVAL_VERSION_MISMATCH", "CHANGE_PACKAGE_APPROVAL_HASH_MISMATCH",
                "CHANGE_PACKAGE_LANDING_VERSION_MISMATCH", "CHANGE_PACKAGE_LANDING_HASH_MISMATCH",
                "APPROVED_LANDING_DISABLED", "WORK_SESSION_NOT_WAITING_APPROVAL").contains(reason);
    }

}
