package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.Map;

/** Output returned by a Graph node body before lifecycle projection. */
record OpsGraphNodeExecutionResult(String output, Map<String, Object> result) {
}
