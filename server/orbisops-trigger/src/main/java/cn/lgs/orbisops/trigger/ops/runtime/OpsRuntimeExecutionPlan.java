package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime routing result produced by the rule router before an Agent task runs.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsRuntimeExecutionPlan {

    private String mode;
    private String engine;
    private String adapterKey;
    private boolean memoryEnabled;
    private boolean hybrid;
    private String reason;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
