package cn.lgs.orbisops.application.toolset;

import java.util.List;
import java.util.Map;

/** Infrastructure boundary for bounded read-only MySQL queries. */
public interface LocalMySqlExecutionPort {

    List<Map<String, Object>> query(
            LocalMySqlExecutionTarget target,
            String sql,
            List<Object> parameters);

}
