package cn.lgs.orbisops.trigger.ops.runtime;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.intValue;
import static cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpConfigValues.text;

/** Projects the bounded local service-control MCP environment. */
final class OpsServiceControlEnvironmentProjector {

    Map<String, String> project(String endpoint, Map<String, Object> permission) {
        String service = OpsServiceControlResourceIdentity.serviceName(endpoint);
        Map<String, String> env = new LinkedHashMap<>();
        env.put("SERVICE_CONTROL_ALLOWED_SERVICES", service);
        String configuredStateFile = text(System.getenv("SERVICE_CONTROL_STATE_FILE"), "");
        env.put("SERVICE_CONTROL_STATE_FILE", configuredStateFile.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"),
                        "orbisops-service-control",
                        service.replaceAll("[^A-Za-z0-9_.-]+", "-") + ".json").toString()
                : configuredStateFile);
        forwardEnvironment(env, "SERVICE_CONTROL_EMERGENCY_STOP");
        forwardEnvironment(env, "SERVICE_CONTROL_TEST_MODE");
        forwardEnvironment(env, "SERVICE_CONTROL_TEST_POST_COMMIT_ACTION");
        forwardEnvironment(env, "SERVICE_CONTROL_TEST_POST_COMMIT_MATCH");
        env.put("SERVICE_CONTROL_LOCK_TIMEOUT_MS",
                String.valueOf(intValue(permission.get("lockTimeoutMs"), 5000)));
        return env;
    }

    private void forwardEnvironment(Map<String, String> target, String key) {
        String value = text(System.getenv(key), "");
        if (!value.isBlank()) target.put(key, value);
    }
}
