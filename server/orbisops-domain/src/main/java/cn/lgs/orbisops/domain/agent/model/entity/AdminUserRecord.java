package cn.lgs.orbisops.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员/普通用户账号记录。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserRecord {

    private Long id;
    private String userId;
    private String username;
    private String password;
    private String userRole;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
