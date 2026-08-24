package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员用户登录请求 DTO
 *
 * @description 管理员用户登录请求数据传输对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserLoginRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户名（登录账号）
     */
    private String username;

    /** New clients submit phone; username remains for legacy API clients. */
    @ToString.Exclude
    private String phone;

    /**
     * 密码
     */
    @ToString.Exclude
    private String password;

}
