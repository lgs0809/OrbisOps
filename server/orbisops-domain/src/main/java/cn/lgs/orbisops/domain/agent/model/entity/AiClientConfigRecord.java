package cn.lgs.orbisops.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 客户端配置聚合记录。
 * <p>
 * 该对象是应用层和基础设施层之间的端口数据对象，避免应用层直接依赖 MyBatis PO。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientConfigRecord {

    private Long id;

    private String clientId;
    private String clientName;
    private String description;

    private String apiId;
    private String providerName;
    private String providerType;
    private String baseUrl;
    private String apiKey;
    private String completionsPath;
    private String embeddingsPath;

    private String modelId;
    private String modelName;
    private String modelType;
    private String modelUsage;

    private String mcpId;
    private String mcpName;
    private String transportType;
    private String transportConfig;
    private Integer requestTimeout;

    private String advisorId;
    private String advisorName;
    private String advisorType;
    private Integer orderNum;
    private String extParam;

    private String promptId;
    private String promptName;
    private String promptContent;

    private String ragId;
    private String ragName;
    private String knowledgeTag;

    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
