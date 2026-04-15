package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import java.util.Map;
import java.util.Set;

/** Renders only a complete stored candidate identity, never a failed tool invocation. */
final class OpsPreparedChangePackageSummary {
    private static final Set<String> CANDIDATE_STATES = Set.of(
            "DRAFT", "VALIDATING", "VALIDATION_FAILED", "REVISING", "READY_FOR_REVIEW", "REVIEWING");

    static String renderReviewReady(Object output) {
        try {
            Object parsed = output instanceof Map<?, ?> ? output : JSON.parse(String.valueOf(output));
            if (!(parsed instanceof Map<?, ?> result)
                    || !Set.of("READY_FOR_REVIEW", "REVIEWING").contains(text(result.get("status")))) return null;
            return render(parsed);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    static String render(Object output) {
        try {
            Object parsed = output instanceof Map<?, ?> ? output : JSON.parse(String.valueOf(output));
            if (!(parsed instanceof Map<?, ?> result)) return null;
            String id = text(result.get("packageId"));
            String status = text(result.get("status"));
            String hash = text(result.get("packageHash"));
            Object version = result.get("version");
            if (id.isBlank() || !CANDIDATE_STATES.contains(status) || !hash.matches("[a-f0-9]{64}")
                    || !(version instanceof Number number) || number.longValue() < 1
                    || result.containsKey("error") || Boolean.FALSE.equals(result.get("allowed"))) return null;
            String next = switch (status) {
                case "READY_FOR_REVIEW" -> "方案已具备提交审核条件，请在变更中心审核后提交。";
                case "REVIEWING" -> "方案已提交，等待人工审批。";
                case "VALIDATION_FAILED" -> "验证未通过，需要补齐或修正方案，当前不能审批落地。";
                default -> "方案仍在准备或验证阶段，尚未具备审批落地条件。";
            };
            return "已保存变更候选方案，尚未执行生产变更。\n"
                    + "- ChangePackage：`" + id + "`\n- 状态：`" + status + "`\n"
                    + "- 版本：" + version + "\n- 后续：" + next;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
