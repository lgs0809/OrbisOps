package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluationContext;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleEvaluator;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson.JacksonJsonSchemaValidatorSupplier;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Frozen node exit contract. JSON Schema and the existing bounded Rule AST are data, never executable code. */
final class OpsWorkflowNodeOutputContract {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final JsonSchemaValidator SCHEMAS = new JacksonJsonSchemaValidatorSupplier().get();
    private static final int MAX_BYTES = 1024 * 1024;
    private final String format;
    private final Map<String, Object> schema;
    private final WorkflowRule rule;

    private OpsWorkflowNodeOutputContract(String format, Map<String, Object> schema, WorkflowRule rule) {
        this.format = format;
        this.schema = schema;
        this.rule = rule;
    }

    static OpsWorkflowNodeOutputContract compile(OpsWorkflowNode node) {
        Object configured = node.getConfig() == null ? null : node.getConfig().get("outputContract");
        if (configured == null) return new OpsWorkflowNodeOutputContract("LEGACY", Map.of(), null);
        if (!(configured instanceof Map<?, ?> config)) throw invalid("OBJECT_REQUIRED");
        for (Object key : config.keySet()) {
            if (!Set.of("format", "schema", "rule").contains(key)) throw invalid("UNKNOWN_FIELD:" + key);
        }
        String format = String.valueOf(config.get("format"));
        if (!Set.of("JSON", "TEXT").contains(format)) throw invalid("FORMAT_REQUIRED");
        Map<String, Object> schema = Map.of();
        if (config.containsKey("schema")) {
            if (!"JSON".equals(format) || !(config.get("schema") instanceof Map<?, ?>)) {
                throw invalid("SCHEMA_OBJECT_REQUIRED_FOR_JSON");
            }
            schema = JSON.convertValue(config.get("schema"), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            rejectRemoteReferences(schema);
        } else if ("JSON".equals(format)) throw invalid("JSON_SCHEMA_REQUIRED");
        WorkflowRule rule = null;
        if (config.containsKey("rule")) {
            Object source = config.get("rule");
            rule = new OpsWorkflowRuleCompilerAdapter().parseExpression(
                    source instanceof String text ? text : json(source));
        }
        return new OpsWorkflowNodeOutputContract(format, schema, rule);
    }

    /** Remove only a sole, exact published output-key wrapper whose contents already satisfy the schema. */
    String normalize(String output, String outputKey) {
        if (!"JSON".equals(format) || output == null || outputKey == null || outputKey.isBlank()
                || output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) return output;
        try {
            Object value = JSON.readValue(output, Object.class);
            if (SCHEMAS.validate(schema, value).valid()) return output;
            if (value instanceof Map<?, ?> fields && fields.size() == 1 && fields.containsKey(outputKey)
                    && fields.get(outputKey) instanceof Map<?, ?> nested && SCHEMAS.validate(schema, nested).valid())
                return json(nested);
        } catch (JsonProcessingException malformed) {
            // Retain invalid model output; the normal completion boundary rejects it with its original reason.
        }
        return output;
    }

    void validate(BoundWorkflowExecutionPlan plan, String nodeId, Map<String, Object> output) {
        if (output == null || output.isEmpty()) throw invalid("EMPTY");
        if (json(output).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid("TOO_LARGE");
        }
        Object value = output.containsKey("output") ? output.get("output") : output;
        if (value == null) throw invalid("NULL");
        if (value instanceof String text) OpsAgentOutputGuard.assertSuccessful(text);
        if ("TEXT".equals(format) && (!(value instanceof String text) || text.isBlank())) {
            throw invalid("TEXT_REQUIRED");
        }
        if ("JSON".equals(format)) {
            if (value instanceof String text) {
                try { value = JSON.readValue(text, Object.class); }
                catch (JsonProcessingException error) { throw invalid("JSON_INVALID"); }
            }
            if (!SCHEMAS.validate(schema, value).valid()) throw invalid("SCHEMA_MISMATCH");
        }
        validateClaims(plan, nodeId, output);
        if (value instanceof Map<?, ?> fields) validateClaims(plan, nodeId, fields);
        if (rule != null && !new WorkflowRuleEvaluator().evaluate(rule, new WorkflowRuleEvaluationContext(
                Map.of("nodeOutput", value, "runtime", Map.of(
                        "runId", plan.runId(), "projectId", plan.projectId(),
                        "definitionVersion", plan.definitionVersion(), "nodeId", nodeId))))) {
            throw invalid("BUSINESS_RULE_REJECTED");
        }
    }

    private void validateClaims(BoundWorkflowExecutionPlan plan, String nodeId, Map<?, ?> fields) {
        Map<String, Object> identity = Map.of(
                "runId", plan.runId(), "sessionId", plan.sessionId(), "projectId", plan.projectId(),
                "nodeId", nodeId, "agentId", plan.agentId(), "agentVersion", plan.definitionVersion(),
                "definitionHash", plan.definitionHash(), "planHash", plan.planHash());
        identity.forEach((key, expected) -> {
            if (fields.containsKey(key) && !Objects.equals(String.valueOf(expected), String.valueOf(fields.get(key)))) {
                throw invalid("IDENTITY_MISMATCH:" + key);
            }
        });
    }

    private static void rejectRemoteReferences(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (Set.of("$ref", "$dynamicRef", "$recursiveRef").contains(entry.getKey())
                        && !String.valueOf(entry.getValue()).startsWith("#")) {
                    throw invalid("REMOTE_SCHEMA_REFERENCE_FORBIDDEN");
                }
                rejectRemoteReferences(entry.getValue());
            }
        } else if (value instanceof Iterable<?> items) items.forEach(OpsWorkflowNodeOutputContract::rejectRemoteReferences);
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw invalid("NOT_JSON_SERIALIZABLE"); }
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("WORKFLOW_NODE_OUTPUT_" + reason);
    }
}
