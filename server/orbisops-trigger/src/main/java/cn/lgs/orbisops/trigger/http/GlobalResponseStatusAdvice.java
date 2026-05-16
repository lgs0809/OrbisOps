package cn.lgs.orbisops.trigger.http;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class GlobalResponseStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // An exception handler returning ResponseEntity owns its explicit error
        // status. The legacy body-code mapper must not turn its 409 into 500.
        if (returnType != null && org.springframework.http.ResponseEntity.class.isAssignableFrom(returnType.getParameterType())
                && response instanceof org.springframework.http.server.ServletServerHttpResponse servlet
                && servlet.getServletResponse().getStatus() >= 400) {
            return body;
        }
        if (body instanceof Response<?> apiResponse) {
            HttpStatus status = mapStatus(apiResponse.getCode(), apiResponse.getInfo());
            if (status != null) {
                response.setStatusCode(status);
            }
        }
        return body;
    }

    private HttpStatus mapStatus(String code, String info) {
        if (ResponseCode.SUCCESS.getCode().equals(code)) {
            return null;
        }
        if (ResponseCode.ILLEGAL_PARAMETER.getCode().equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ResponseCode.NO_LOGIN.getCode().equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        String message = info == null ? "" : info;
        if (containsAny(message, "WAITING_SANDBOX", "SANDBOX_UNAVAILABLE", "SANDBOX_FAILED",
                "SANDBOX_STALE", "SANDBOX_UNTRUSTED_COMMAND", "MCP_TOOL_REQUIRES_CHANGE_PACKAGE",
                "SKIP_DUPLICATE_FROZEN", "SKIP_SIMILAR_SKILL_FROZEN",
                "READY_FOR_REVIEW", "REVIEWING", "NEEDS_REPLAN", "未处于 APPROVED", "不能落地",
                "已存在", "重复", "冲突", "版本尚未发布", "状态不允许", "当前状态不允许", "不能切换", "不允许")) {
            return HttpStatus.CONFLICT;
        }
        if (ResponseCode.FORBIDDEN.getCode().equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if (containsAny(message, "未登录", "登录已过期", "未获取到已认证")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (containsAny(message, "无权限", "权限不足", "禁止访问")) {
            return HttpStatus.FORBIDDEN;
        }
        if (containsAny(message, "不存在", "未找到", "找不到", "不存在：")) {
            return HttpStatus.NOT_FOUND;
        }
        if (containsAny(message, "参数", "不能为空", "必须提供", "缺少", "非法", "无效")) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
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
}
