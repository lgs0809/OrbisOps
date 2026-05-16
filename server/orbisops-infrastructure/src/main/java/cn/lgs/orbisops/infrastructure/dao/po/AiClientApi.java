package cn.lgs.orbisops.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI客户端API配置表
 * @description AI客户端API配置表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientApi {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * API ID
     */
    private String apiId;

    /**
     * Provider 名称
     */
    private String providerName;

    /**
     * Provider 类型
     */
    private String providerType;

    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 对话补全路径
     */
    private String completionsPath;

    /**
     * 嵌入向量路径
     */
    private String embeddingsPath;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
