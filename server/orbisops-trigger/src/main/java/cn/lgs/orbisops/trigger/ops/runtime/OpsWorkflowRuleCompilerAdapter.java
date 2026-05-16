package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;
import cn.lgs.orbisops.domain.agentdefinition.rule.AllRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.AnyRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.ChangedRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.ComparisonRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.ExistsRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.InRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.NotRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowComparisonOperator;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRule;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleCompiler;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRuleField;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict protocol ACL for JSON Rule AST plus bounded legacy migration expressions. */
public final class OpsWorkflowRuleCompilerAdapter implements WorkflowRuleCompiler {

    private static final int MAX_DEPTH = 12;
    private static final int MAX_NODES = 128;
    private static final Pattern COMPARISON = Pattern.compile(
            "^([#A-Za-z_][A-Za-z0-9_.]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
    private static final Set<String> FORBIDDEN_TOKENS = Set.of(
            "T(", "new ", "getClass", ".class", "@", "[", "]", "?.", "#state");

    @Override
    public WorkflowRule compile(
            AgentWorkflowRouteMode routeMode,
            String expression) {
        if (routeMode == null) throw new IllegalArgumentException("WORKFLOW_ROUTE_MODE_REQUIRED");
        String source = expression == null ? "" : expression.trim();
        return switch (routeMode) {
            case ALWAYS, DEFAULT -> new AllRule(List.of());
            case ROUTE_MATCH -> new InRule(
                    new WorkflowRuleField("runtime.selectedRoutes"),
                    List.of(required(source, "WORKFLOW_ROUTE_MATCH_VALUE_REQUIRED")));
            case REVIEW_DECISION -> reviewDecisionRule(source);
            case CONTAINS -> new InRule(
                    new WorkflowRuleField("nodeOutput.output"),
                    List.of(required(source, "WORKFLOW_CONTAINS_VALUE_REQUIRED")));
            case ERROR -> source.isBlank()
                    ? new ExistsRule(new WorkflowRuleField("error.message"))
                    : new InRule(new WorkflowRuleField("error.message"), List.of(source));
            case EXPRESSION -> parseExpression(source);
        };
    }

    public WorkflowRule parseExpression(String expression) {
        String source = required(expression, "WORKFLOW_RULE_EXPRESSION_REQUIRED");
        ParseState state = new ParseState();
        if (source.startsWith("{")) {
            try {
                return parseJson(JSON.parseObject(source), 1, state);
            } catch (IllegalArgumentException error) {
                throw error;
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("WORKFLOW_RULE_JSON_INVALID", error);
            }
        }
        rejectForbidden(source);
        return parseLegacy(source, 1, state);
    }

    private WorkflowRule parseJson(JSONObject object, int depth, ParseState state) {
        enter(depth, state);
        if (object == null) throw new IllegalArgumentException("WORKFLOW_RULE_OBJECT_REQUIRED");
        String op = required(object.getString("op"), "WORKFLOW_RULE_OP_REQUIRED")
                .toUpperCase(Locale.ROOT);
        return switch (op) {
            case "ALL" -> {
                assertKeys(object, Set.of("op", "rules"));
                yield new AllRule(parseRules(object.getJSONArray("rules"), depth, state));
            }
            case "ANY" -> {
                assertKeys(object, Set.of("op", "rules"));
                yield new AnyRule(parseRules(object.getJSONArray("rules"), depth, state));
            }
            case "NOT" -> {
                assertKeys(object, Set.of("op", "rule"));
                yield new NotRule(parseJson(object.getJSONObject("rule"), depth + 1, state));
            }
            case "EQ", "NE", "GT", "GTE", "LT", "LTE" -> {
                assertKeys(object, Set.of("op", "field", "value"));
                yield new ComparisonRule(
                        new WorkflowRuleField(object.getString("field")),
                        WorkflowComparisonOperator.valueOf(op),
                        literal(object.get("value")));
            }
            case "EXISTS" -> {
                assertKeys(object, Set.of("op", "field"));
                yield new ExistsRule(new WorkflowRuleField(object.getString("field")));
            }
            case "IN" -> {
                assertKeys(object, Set.of("op", "field", "values"));
                JSONArray values = object.getJSONArray("values");
                if (values == null) throw new IllegalArgumentException("WORKFLOW_RULE_IN_VALUES_REQUIRED");
                List<Object> candidates = new ArrayList<>();
                for (Object value : values) candidates.add(literal(value));
                yield new InRule(new WorkflowRuleField(object.getString("field")), candidates);
            }
            case "CHANGED" -> {
                assertKeys(object, Set.of("op", "field"));
                yield new ChangedRule(new WorkflowRuleField(object.getString("field")));
            }
            default -> throw new IllegalArgumentException("WORKFLOW_RULE_OP_UNSUPPORTED:" + op);
        };
    }

    private List<WorkflowRule> parseRules(JSONArray array, int depth, ParseState state) {
        if (array == null) throw new IllegalArgumentException("WORKFLOW_RULE_CHILDREN_REQUIRED");
        List<WorkflowRule> rules = new ArrayList<>();
        for (Object item : array) {
            if (!(item instanceof JSONObject object)) {
                throw new IllegalArgumentException("WORKFLOW_RULE_CHILD_OBJECT_REQUIRED");
            }
            rules.add(parseJson(object, depth + 1, state));
        }
        return rules;
    }

    private WorkflowRule parseLegacy(String expression, int depth, ParseState state) {
        enter(depth, state);
        String source = expression.trim();
        String normalized = source.toLowerCase(Locale.ROOT);
        if ("always".equals(normalized) || "default".equals(normalized)) {
            return new AllRule(List.of());
        }
        if (normalized.startsWith("contains:")) {
            return new InRule(
                    new WorkflowRuleField("nodeOutput.output"),
                    List.of(source.substring("contains:".length()).trim()));
        }
        if ("plan.tasks not empty".equals(normalized)) {
            return new ExistsRule(new WorkflowRuleField("nodeOutput.plan.tasks"));
        }
        if ("evidence_sufficient || round_limit".equals(normalized)) {
            return new AnyRule(List.of(
                    new ComparisonRule(
                            new WorkflowRuleField("runtime.evidence_sufficient"),
                            WorkflowComparisonOperator.EQ,
                            true),
                    new ComparisonRule(
                            new WorkflowRuleField("runtime.round_limit"),
                            WorkflowComparisonOperator.EQ,
                            true)));
        }
        int or = source.indexOf("||");
        if (or >= 0) {
            return new AnyRule(List.of(
                    parseLegacy(source.substring(0, or), depth + 1, state),
                    parseLegacy(source.substring(or + 2), depth + 1, state)));
        }
        int and = source.indexOf("&&");
        if (and >= 0) {
            return new AllRule(List.of(
                    parseLegacy(source.substring(0, and), depth + 1, state),
                    parseLegacy(source.substring(and + 2), depth + 1, state)));
        }
        if (source.startsWith("!")) {
            return new NotRule(parseLegacy(source.substring(1), depth + 1, state));
        }
        Matcher matcher = COMPARISON.matcher(source);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("WORKFLOW_RULE_LEGACY_EXPRESSION_UNSUPPORTED:" + source);
        }
        return new ComparisonRule(
                new WorkflowRuleField(fieldPath(matcher.group(1))),
                operator(matcher.group(2)),
                parseLiteral(matcher.group(3)));
    }

    private WorkflowRule reviewDecisionRule(String expression) {
        String expected = required(expression, "WORKFLOW_REVIEW_DECISION_REQUIRED");
        return new AnyRule(List.of(
                new InRule(new WorkflowRuleField("approval.decision"), List.of(expected)),
                new InRule(new WorkflowRuleField("nodeOutput.output"), List.of(expected))));
    }

    private String fieldPath(String source) {
        String field = source.trim();
        if ("#decision".equals(field) || "#output".equals(field)) {
            return "nodeOutput.output";
        }
        if (field.startsWith("#")) {
            throw new IllegalArgumentException("WORKFLOW_RULE_VARIABLE_FORBIDDEN:" + field);
        }
        if (field.startsWith("plan.")) return "nodeOutput." + field;
        for (String root : WorkflowRuleField.allowedRoots()) {
            if (field.startsWith(root + ".")) return field;
        }
        if (field.contains(".")) {
            String root = field.substring(0, field.indexOf('.'));
            throw new IllegalArgumentException(
                    "WORKFLOW_RULE_FIELD_ROOT_FORBIDDEN:" + root);
        }
        return "runtime." + field;
    }

    private WorkflowComparisonOperator operator(String source) {
        return switch (source) {
            case "==" -> WorkflowComparisonOperator.EQ;
            case "!=" -> WorkflowComparisonOperator.NE;
            case ">" -> WorkflowComparisonOperator.GT;
            case ">=" -> WorkflowComparisonOperator.GTE;
            case "<" -> WorkflowComparisonOperator.LT;
            case "<=" -> WorkflowComparisonOperator.LTE;
            default -> throw new IllegalArgumentException("WORKFLOW_RULE_OPERATOR_UNSUPPORTED:" + source);
        };
    }

    private Object parseLiteral(String source) {
        String value = source.trim();
        if ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        if ("null".equalsIgnoreCase(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            if (!value.matches("[A-Za-z0-9_:/.-]+")) {
                throw new IllegalArgumentException("WORKFLOW_RULE_LITERAL_INVALID:" + value);
            }
            return value;
        }
    }

    private Object literal(Object value) {
        if (value instanceof JSONObject || value instanceof JSONArray) {
            throw new IllegalArgumentException("WORKFLOW_RULE_LITERAL_COMPLEX_FORBIDDEN");
        }
        return value;
    }

    private void assertKeys(JSONObject object, Set<String> allowed) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("WORKFLOW_RULE_FIELD_UNKNOWN:" + key);
            }
        }
    }

    private void rejectForbidden(String source) {
        for (String token : FORBIDDEN_TOKENS) {
            if (source.contains(token)) {
                throw new IllegalArgumentException("WORKFLOW_RULE_SYNTAX_FORBIDDEN:" + token);
            }
        }
    }

    private void enter(int depth, ParseState state) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("WORKFLOW_RULE_DEPTH_EXCEEDED:" + depth);
        }
        state.nodes++;
        if (state.nodes > MAX_NODES) {
            throw new IllegalArgumentException("WORKFLOW_RULE_NODE_COUNT_EXCEEDED:" + state.nodes);
        }
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static final class ParseState {
        private int nodes;
    }
}
