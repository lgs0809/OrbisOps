package cn.lgs.orbisops.domain.execution.model;

import java.util.Locale;

public enum ExecutionAdapterType {
    LOCAL_JAVA_SERVICE("local-java-service"),
    DEPLOYMENT_HTTP("deployment-http"),
    MYSQL_CONTROLLED("mysql-controlled"),
    REDIS_CONTROLLED("redis-controlled"),
    RABBITMQ_POLICY("rabbitmq-policy");

    private final String code;

    ExecutionAdapterType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ExecutionAdapterType require(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ExecutionAdapterType type : values()) {
            if (type.code.equals(normalized)) return type;
        }
        throw new IllegalArgumentException("EXECUTION_ADAPTER_UNSUPPORTED:" + normalized);
    }
}
