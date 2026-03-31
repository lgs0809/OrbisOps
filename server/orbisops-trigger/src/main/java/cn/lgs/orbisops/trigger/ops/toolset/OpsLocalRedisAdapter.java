package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalRedisApplicationService;

import java.util.Map;

/** Redis local read/inspection tool protocol adapter. */
public final class OpsLocalRedisAdapter {

    private final LocalRedisApplicationService service;
    private final OpsLocalAdapterSettings settings;

    public OpsLocalRedisAdapter(
            LocalRedisApplicationService service,
            OpsLocalAdapterSettings settings) {
        if (service == null) throw new IllegalArgumentException("LOCAL_REDIS_SERVICE_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("LOCAL_ADAPTER_SETTINGS_REQUIRED");
        this.service = service;
        this.settings = settings;
    }

    public Map<String, Object> execute(
            String toolName,
            OpsLocalToolArguments args) {
        String key = args.text("key");
        return switch (toolName) {
            case "redis_info" -> Map.of(
                    "status", "SUCCEEDED",
                    "info", service.info());
            case "redis_get" -> Map.of(
                    "status", "SUCCEEDED",
                    "key", key,
                    "value", service.get(key));
            case "redis_ttl" -> Map.of(
                    "status", "SUCCEEDED",
                    "key", key,
                    "ttl", service.ttl(key));
            case "redis_type" -> Map.of(
                    "status", "SUCCEEDED",
                    "key", key,
                    "type", service.type(key));
            case "redis_memory_usage" -> Map.of(
                    "status", "NOT_SUPPORTED",
                    "trustedProof", false,
                    "message", "当前 Redis adapter 不暴露 MEMORY USAGE，未伪造结果。");
            case "redis_scan", "redis_key_sample_check", "redis_namespace_check" -> {
                String pattern = args.text("pattern");
                yield Map.of(
                        "status", "SUCCEEDED",
                        "pattern", pattern,
                        "keys", service.scan(
                                pattern,
                                args.raw("limit"),
                                settings.maxRows()));
            }
            case "redis_dry_run_expire" -> Map.of(
                    "status", "NOT_SUPPORTED",
                    "trustedProof", false,
                    "message", "Redis expire dry-run 不执行生产写；需要 approved package landing 才能真实 EXPIRE。");
            default -> throw new IllegalArgumentException(
                    "未知 Redis 工具：" + toolName);
        };
    }
}
