package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class CacheToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "cache";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(
                definitions.toolset(
                        "cache.redis.readonly",
                        "Redis 只读",
                        "查询 key、TTL 和只读诊断",
                        "LOCAL_REDIS",
                        true,
                        List.of(
                                tools.read("redis_info", "Redis INFO", "LOCAL_REDIS"),
                                tools.read("redis_get", "读取 Redis key", "LOCAL_REDIS"),
                                tools.read("redis_ttl", "查询 TTL", "LOCAL_REDIS"),
                                tools.read("redis_type", "查询类型", "LOCAL_REDIS"),
                                tools.read("redis_scan", "扫描 key", "LOCAL_REDIS"),
                                tools.read("redis_memory_usage", "查询内存占用", "LOCAL_REDIS"),
                                tools.validation("redis_namespace_check", "Redis namespace 检查"),
                                tools.validation("redis_dry_run_expire", "Redis expire dry-run 占位，不伪造成功"),
                                tools.validation("redis_key_sample_check", "Redis key 样本检查"))),
                definitions.targetWrite(
                        "cache.redis.change",
                        "Redis 变更",
                        "redis_mutate"));
    }
}
