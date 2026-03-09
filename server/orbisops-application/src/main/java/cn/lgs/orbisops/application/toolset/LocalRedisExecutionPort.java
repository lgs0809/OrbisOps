package cn.lgs.orbisops.application.toolset;

import java.util.List;

/** Infrastructure boundary for local read-only Redis diagnostics. */
public interface LocalRedisExecutionPort {

    String info();

    String get(String key);

    Long ttl(String key);

    String type(String key);

    List<String> scan(String pattern, int limit);

}
