package cn.lgs.orbisops.domain.memory.service;

import java.util.regex.Pattern;

public final class MemoryContentPolicy {

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|credential|access[_ -]?key|api[_ -]?key|私钥|密码|口令)");
    private static final Pattern HARD_POLICY = Pattern.compile(
            "(?i)(绕过|跳过).{0,8}(审批|沙箱|执行中心|toolset|router)|关闭审计|直接重启生产|直接删(?:除)?数据|直接执行(?:任意)?\\s*sql|放宽.*权限");
    private static final Pattern EPHEMERAL_RUNTIME_FACT = Pattern.compile(
            "(?i)(trace[_ -]?id|order[_ -]?id|container[_ -]?id|request[_ -]?id|span[_ -]?id|一次性(?:日志|指标|错误)|本次(?:日志|指标|工具结果)|redis\\s*key\\s*(样本|值)|prometheus.{0,12}=[0-9.]+)");

    public void requireAllowed(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isBlank()) throw new IllegalArgumentException("MEMORY_CONTENT_REQUIRED");
        if (SECRET.matcher(value).find()) {
            throw new SecurityException("SECRET_OR_CREDENTIAL：凭据不能写入 Memory");
        }
        if (HARD_POLICY.matcher(value).find()) {
            throw new SecurityException("POLICY_VIOLATION：权限、审批和生产执行策略不能写入 Memory");
        }
        if (EPHEMERAL_RUNTIME_FACT.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "MEMORY_EPHEMERAL_FACT_REJECTED：单次 trace、订单、容器、指标、日志或工具结果不能写入长期 Memory");
        }
    }
}
