package cn.lgs.orbisops.domain.agentdefinition.rule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class WorkflowRuleValuePolicy {

    private static final int MAX_STRING_LENGTH = 4096;
    private static final int MAX_LIST_SIZE = 256;

    private WorkflowRuleValuePolicy() {
    }

    static Object normalize(Object value) {
        if (value == null || value instanceof Boolean || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger) {
            return new BigDecimal(String.valueOf(value));
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("WORKFLOW_RULE_NUMBER_NON_FINITE");
            }
            return new BigDecimal(String.valueOf(value));
        }
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("WORKFLOW_RULE_STRING_TOO_LARGE");
            }
            return text;
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_LIST_SIZE) {
                throw new IllegalArgumentException("WORKFLOW_RULE_LIST_TOO_LARGE");
            }
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) normalized.add(normalize(item));
            return List.copyOf(normalized);
        }
        throw new IllegalArgumentException(
                "WORKFLOW_RULE_VALUE_TYPE_FORBIDDEN:" + value.getClass().getSimpleName());
    }
}
