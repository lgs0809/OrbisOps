package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps technical resource identity and unsupported operational premises out of free-form Query Rewrite.
 * Query Rewrite may resolve conversational references to business language, but technical resource identity
 * belongs to current user input or authoritative project metadata, and it may not strengthen uncertain user
 * language into a confirmed operational fact.
 */
final class OpsQueryRewriteResourceIntegrityPolicy {

    private static final Pattern API_PATH = Pattern.compile(
            "(?i)(/api/[a-z0-9_./{}:-]+)");
    private static final List<List<String>> STRONG_OPERATIONAL_PREMISES = List.of(
            List.of("已确认", "已经确认", "故障已确认", "故障已经确认", "已证实", "已经证实"),
            List.of("仍在持续", "故障仍在", "持续故障", "故障持续中"),
            List.of("已定位", "已经定位", "已查明", "已经查明"),
            List.of("已恢复", "已经恢复", "确认已恢复"));

    boolean introducesUnknownApiPath(
            String originalQuery,
            String memoryContext,
            String rewrittenQuery) {
        Set<String> explicitUserPaths = paths(originalQuery);
        Set<String> rewritten = paths(rewrittenQuery);
        return rewritten.stream().anyMatch(path -> !explicitUserPaths.contains(path));
    }

    boolean introducesUnsupportedOperationalPremise(
            String originalQuery,
            String rewrittenQuery) {
        String original = value(originalQuery);
        String rewritten = value(rewrittenQuery);
        return STRONG_OPERATIONAL_PREMISES.stream().anyMatch(group ->
                group.stream().anyMatch(rewritten::contains)
                        && group.stream().noneMatch(original::contains));
    }

    private Set<String> paths(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = API_PATH.matcher(value(value));
        while (matcher.find()) {
            result.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
