package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpAuthoritativeSchemaHydrationService;
import io.modelcontextprotocol.json.schema.jackson.JacksonJsonSchemaValidatorSupplier;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Validates future read-only checks through tools/list; never invokes the future operation. */
@Component
public final class OpsPreparePostCheckValidator {
    private final OpsMcpAuthoritativeSchemaHydrationService schemas;

    public OpsPreparePostCheckValidator(OpsMcpAuthoritativeSchemaHydrationService schemas) {
        this.schemas = java.util.Objects.requireNonNull(schemas);
    }

    void validate(String projectId, Map<String, Object> check, McpToolPolicy policy, String resourceScope) {
        String tool = String.valueOf(check.getOrDefault("toolName", ""));
        if (policy == null || !policy.activeAndHumanReviewed() || !policy.readOnly() || !policy.landAllowed())
            throw invalid(tool, "READ_ONLY_REVIEWED_POLICY_REQUIRED");
        if (!(check.get("expectedValues") instanceof Map<?, ?> expected) || expected.isEmpty())
            throw invalid(tool, "EXPECTED_VALUES_REQUIRED");
        String identity = String.valueOf(check.getOrDefault("resourceIdentityField", "resourceKey"));
        if (resourceScope.isBlank() || !resourceScope.equals(expected.get(identity)))
            throw invalid(tool, "EXPECTED_RESOURCE_IDENTITY_MISMATCH");
        if (!(check.get("arguments") instanceof Map<?, ?> arguments)) throw invalid(tool, "ARGUMENTS_OBJECT_REQUIRED");
        var schema = schemas.hydrate(projectId, Map.of("mcpId", policy.mcpId(), "remoteToolName", tool));
        if (schema == null || !schema.schemaHydrated() || !policy.schemaHash().equals(schema.schemaHash())
                || !(schema.inputSchema() instanceof Map<?, ?> input)) throw invalid(tool, "SCHEMA_CHANGED_OR_MISSING");
        rejectRemoteReferences(input);
        @SuppressWarnings("unchecked") var contract = (Map<String, Object>) input;
        if (!new JacksonJsonSchemaValidatorSupplier().get().validate(contract, arguments).valid())
            throw invalid(tool, "ARGUMENTS_SCHEMA_MISMATCH: 按该只读工具披露的 schema 修正 arguments，不能猜测项目或资源身份");
    }

    private void rejectRemoteReferences(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if ("$ref".equals(entry.getKey()) && !String.valueOf(entry.getValue()).startsWith("#"))
                    throw invalid("", "REMOTE_SCHEMA_REFERENCE_FORBIDDEN");
                rejectRemoteReferences(entry.getValue());
            }
        } else if (value instanceof Iterable<?> values) values.forEach(this::rejectRemoteReferences);
    }

    private IllegalArgumentException invalid(String tool, String reason) {
        return new IllegalArgumentException("CHANGE_PACKAGE_POST_CHECK_INVALID:" + tool + ":" + reason);
    }
}
