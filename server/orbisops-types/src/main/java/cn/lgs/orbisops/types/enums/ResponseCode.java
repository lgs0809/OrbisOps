package cn.lgs.orbisops.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    LOGIN_FAILED("0003", "登录失败"),
    NO_LOGIN("0004", "未登录或登录已过期"),
    FORBIDDEN("0005", "无权限"),
    RATE_LIMITED("0006", "请求过于频繁"),
    ;

    private String code;
    private String info;

}
