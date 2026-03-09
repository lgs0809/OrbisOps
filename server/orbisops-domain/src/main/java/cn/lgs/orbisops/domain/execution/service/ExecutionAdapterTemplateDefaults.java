package cn.lgs.orbisops.domain.execution.service;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;

import java.util.List;
import java.util.Map;

public final class ExecutionAdapterTemplateDefaults {

    private ExecutionAdapterTemplateDefaults() {
    }

    public static List<ExecutionAdapterTemplate> values() {
        return List.of(
                template(
                        "local-java-service-template",
                        "本地 Java 制品部署模板",
                        ExecutionAdapterType.LOCAL_JAVA_SERVICE,
                        List.of("ARTIFACT_DEPLOY"),
                        ExecutionRiskLevel.HIGH,
                        "用于受控发布本地 Java 制品，项目实例必须配置服务、制品目录和回滚路径。"),
                template(
                        "deployment-http-template",
                        "部署平台 HTTP API 模板",
                        ExecutionAdapterType.DEPLOYMENT_HTTP,
                        List.of("SERVICE_RESTART", "SERVICE_SCALE"),
                        ExecutionRiskLevel.HIGH,
                        "用于调用项目部署平台 API，项目实例必须配置 baseUrl、tokenFile 和环境范围。"),
                template(
                        "mysql-controlled-template",
                        "MySQL 受控执行模板",
                        ExecutionAdapterType.MYSQL_CONTROLLED,
                        List.of(
                                "MYSQL_CREATE_INDEX",
                                "MYSQL_UPDATE_LIMITED",
                                "MYSQL_DROP_INDEX",
                                "MYSQL_SET_GLOBAL_VARIABLE"),
                        ExecutionRiskLevel.CRITICAL,
                        "用于 MySQL 受控变更，项目实例必须配置可见对象、允许动作、幂等表和凭据文件。"),
                template(
                        "redis-controlled-template",
                        "Redis 受控执行模板",
                        ExecutionAdapterType.REDIS_CONTROLLED,
                        List.of(
                                "REDIS_DELETE_KEYS",
                                "REDIS_UPDATE_TTL",
                                "REDIS_CONFIG_SET"),
                        ExecutionRiskLevel.CRITICAL,
                        "用于 Redis 受控变更，项目实例必须配置 key pattern、动作范围、快照限制和凭据文件。"),
                template(
                        "rabbitmq-policy-template",
                        "RabbitMQ Policy 受控执行模板",
                        ExecutionAdapterType.RABBITMQ_POLICY,
                        List.of("RABBITMQ_UPSERT_POLICY"),
                        ExecutionRiskLevel.HIGH,
                        "用于 RabbitMQ Policy 受控变更，项目实例必须配置 vhost、policy 前缀和 definition 白名单。"));
    }

    private static ExecutionAdapterTemplate template(
            String id,
            String name,
            ExecutionAdapterType adapterType,
            List<String> supportedActions,
            ExecutionRiskLevel riskLevel,
            String description) {
        return new ExecutionAdapterTemplate(
                0L,
                id,
                name,
                adapterType,
                supportedActions,
                Map.of("adapterTemplateId", id),
                riskLevel,
                false,
                description,
                ExecutionResourceStatus.ENABLED,
                "system",
                null,
                null);
    }
}
