package cn.lgs.orbisops.domain.agentdefinition.rule;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Deterministic evaluator. It reads maps only and never invokes object methods reflectively. */
public final class WorkflowRuleEvaluator {

    private final WorkflowRuleLimits limits;

    public WorkflowRuleEvaluator() {
        this(WorkflowRuleLimits.DEFAULT);
    }

    public WorkflowRuleEvaluator(WorkflowRuleLimits limits) {
        if (limits == null) throw new IllegalArgumentException("WORKFLOW_RULE_LIMITS_REQUIRED");
        this.limits = limits;
    }

    public boolean evaluate(
            WorkflowRule rule,
            WorkflowRuleEvaluationContext context) {
        if (rule == null) throw new IllegalArgumentException("WORKFLOW_RULE_REQUIRED");
        if (context == null) throw new IllegalArgumentException("WORKFLOW_RULE_CONTEXT_REQUIRED");
        Counter counter = new Counter();
        return evaluate(rule, context, 1, counter);
    }

    private boolean evaluate(
            WorkflowRule rule,
            WorkflowRuleEvaluationContext context,
            int depth,
            Counter counter) {
        if (depth > limits.maxDepth()) {
            throw new IllegalArgumentException("WORKFLOW_RULE_DEPTH_EXCEEDED:" + depth);
        }
        counter.nodes++;
        if (counter.nodes > limits.maxNodes()) {
            throw new IllegalArgumentException("WORKFLOW_RULE_NODE_COUNT_EXCEEDED:" + counter.nodes);
        }
        if (rule instanceof AllRule all) {
            for (WorkflowRule child : all.rules()) {
                if (!evaluate(child, context, depth + 1, counter)) return false;
            }
            return true;
        }
        if (rule instanceof AnyRule any) {
            for (WorkflowRule child : any.rules()) {
                if (evaluate(child, context, depth + 1, counter)) return true;
            }
            return false;
        }
        if (rule instanceof NotRule not) {
            return !evaluate(not.rule(), context, depth + 1, counter);
        }
        if (rule instanceof ComparisonRule comparison) {
            return compare(comparison, context.currentRoots());
        }
        if (rule instanceof ExistsRule exists) {
            Resolved resolved = resolve(context.currentRoots(), exists.field());
            return resolved.present() && meaningful(resolved.value());
        }
        if (rule instanceof InRule in) {
            return in(in, context.currentRoots());
        }
        if (rule instanceof ChangedRule changed) {
            Resolved current = resolve(context.currentRoots(), changed.field());
            Resolved previous = resolve(context.previousRoots(), changed.field());
            if (!current.present() || !previous.present()) return false;
            return !safeEquals(current.value(), previous.value());
        }
        throw new IllegalArgumentException(
                "WORKFLOW_RULE_TYPE_UNSUPPORTED:" + rule.getClass().getSimpleName());
    }

    private boolean compare(ComparisonRule rule, Map<String, Object> roots) {
        Resolved resolved = resolve(roots, rule.field());
        if (!resolved.present()) return false;
        Object actual = safeNormalize(resolved.value());
        if (actual == MissingValue.INSTANCE) return false;
        Object expected = rule.expected();
        if (rule.operator() == WorkflowComparisonOperator.EQ) {
            return Objects.equals(actual, expected);
        }
        if (rule.operator() == WorkflowComparisonOperator.NE) {
            return !Objects.equals(actual, expected);
        }
        Integer order = ordering(actual, expected);
        if (order == null) return false;
        return switch (rule.operator()) {
            case GT -> order > 0;
            case GTE -> order >= 0;
            case LT -> order < 0;
            case LTE -> order <= 0;
            case EQ, NE -> false;
        };
    }

    private boolean in(InRule rule, Map<String, Object> roots) {
        Resolved resolved = resolve(roots, rule.field());
        if (!resolved.present() || resolved.value() == null) return false;
        Object actual = resolved.value();
        if (actual instanceof Collection<?> collection) {
            for (Object item : collection) {
                for (Object candidate : rule.candidates()) {
                    if (safeEquals(item, candidate)) return true;
                }
            }
            return false;
        }
        if (actual instanceof String text) {
            for (Object candidate : rule.candidates()) {
                if (candidate instanceof String expected && text.contains(expected)) return true;
            }
        }
        for (Object candidate : rule.candidates()) {
            if (safeEquals(actual, candidate)) return true;
        }
        return false;
    }

    private Integer ordering(Object actual, Object expected) {
        if (actual instanceof BigDecimal left && expected instanceof BigDecimal right) {
            return left.compareTo(right);
        }
        if (actual instanceof String left && expected instanceof String right) {
            return left.compareTo(right);
        }
        return null;
    }

    private boolean safeEquals(Object left, Object right) {
        return Objects.equals(safeNormalize(left), safeNormalize(right));
    }

    private Object safeNormalize(Object value) {
        try {
            return WorkflowRuleValuePolicy.normalize(value);
        } catch (IllegalArgumentException ignored) {
            return MissingValue.INSTANCE;
        }
    }

    private boolean meaningful(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof Collection<?> collection) return !collection.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }

    private Resolved resolve(
            Map<String, Object> roots,
            WorkflowRuleField field) {
        if (roots == null || !roots.containsKey(field.root())) return Resolved.missing();
        Object value = roots.get(field.root());
        for (String segment : field.nestedSegments()) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return Resolved.missing();
            }
            value = map.get(segment);
        }
        return new Resolved(true, value);
    }

    private record Resolved(boolean present, Object value) {
        private static Resolved missing() {
            return new Resolved(false, null);
        }
    }

    private static final class Counter {
        private int nodes;
    }

    private enum MissingValue {
        INSTANCE
    }
}
