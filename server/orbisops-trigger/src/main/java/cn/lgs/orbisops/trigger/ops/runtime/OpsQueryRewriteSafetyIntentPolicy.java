package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prevents query rewrite from laundering safety-critical operational intent.
 *
 * This is not business routing. It only decides whether the original wording must
 * remain authoritative so downstream policy/ReAct can see the user's real safety intent.
 */
final class OpsQueryRewriteSafetyIntentPolicy {

    private static final List<Pattern> SAFETY_CRITICAL_INTENT = List.of(
            Pattern.compile("(?i)(绕过|忽略|跳过|bypass|skip).{0,16}(审批|approval|沙箱|sandbox|landing|验证|verification|审计|audit)"),
            Pattern.compile("(?i)(直接|立即).{0,16}(生产|prod(?:uction)?).{0,16}(修改|变更|写入|重启|执行|sql|delete|update|write|restart|change)"),
            Pattern.compile("(?i)(验证失败|verification\\s+fail(?:ed|ure)?).{0,24}(恢复|resolved|succeeded|成功)"),
            Pattern.compile("(?i)(重复|duplicate).{0,24}(生产副作用|side\\s*effect|执行|request|请求)"),
            Pattern.compile("(?i)(未授权|unauthori[sz]ed).{0,16}(生产|prod(?:uction)?|工具|tool|写|write)"),
            Pattern.compile("(?i)(timeout|超时).{0,20}(failed|失败).{0,20}(重试|retry)"),
            Pattern.compile("(?i)(landing|落地).{0,24}(不做|跳过|without|skip).{0,16}(验证|verification).{0,24}(关闭|resolved|resolve)"));

    boolean mustPreserveOriginal(String query) {
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) {
            return false;
        }
        return SAFETY_CRITICAL_INTENT.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }
}
