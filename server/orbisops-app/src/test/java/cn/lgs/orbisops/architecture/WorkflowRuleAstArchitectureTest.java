package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRuleAstArchitectureTest {

    private static final String RULE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/agentdefinition/rule/";
    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void ruleLanguageMustRemainSealedAllowListedAndMapOnly() throws IOException {
        String rule = read(RULE + "WorkflowRule.java");
        String field = read(RULE + "WorkflowRuleField.java");
        String evaluator = read(RULE + "WorkflowRuleEvaluator.java");

        assertAll(
                () -> assertTrue(rule.contains("sealed interface WorkflowRule")),
                () -> assertTrue(rule.contains("AllRule")),
                () -> assertTrue(rule.contains("ChangedRule")),
                () -> assertTrue(field.contains("\"input\", \"runtime\", \"nodeOutput\", \"toolResult\"")),
                () -> assertTrue(field.contains("\"evidence\", \"approval\", \"error\"")),
                () -> assertTrue(field.contains("WORKFLOW_RULE_FIELD_ROOT_FORBIDDEN")),
                () -> assertTrue(evaluator.contains("value instanceof Map<?, ?> map")),
                () -> assertFalse(evaluator.contains("java.lang.reflect")),
                () -> assertFalse(evaluator.contains("getMethod(")),
                () -> assertFalse(evaluator.contains("invoke(")));
    }

    @Test
    void compilerMustRejectDynamicLanguagesAndActiveSelectorMustHaveStableOrder() throws IOException {
        String compiler = read(RUNTIME + "OpsWorkflowRuleCompilerAdapter.java");
        String selector = read(RUNTIME + "OpsGraphRouteSelector.java");

        assertAll(
                () -> assertTrue(compiler.contains("WORKFLOW_RULE_SYNTAX_FORBIDDEN")),
                () -> assertTrue(compiler.contains("WORKFLOW_RULE_DEPTH_EXCEEDED")),
                () -> assertTrue(compiler.contains("WORKFLOW_RULE_NODE_COUNT_EXCEEDED")),
                () -> assertFalse(compiler.contains("SpelExpressionParser")),
                () -> assertFalse(compiler.contains("GroovyShell")),
                () -> assertFalse(compiler.contains("ScriptEngine")),
                () -> assertFalse(compiler.contains("Runtime.getRuntime")),
                () -> assertTrue(selector.contains("sorted(edgeOrder())")),
                () -> assertTrue(selector.contains("thenComparing(this::stableEdgeId)")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
