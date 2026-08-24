package cn.lgs.orbisops.domain.skill.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Recognizes only explicit single prohibitions; ambiguous instructions remain rejected. */
final class SkillForbiddenDirectivePolicy {
    private static final Pattern CLAUSE = Pattern.compile("[。；;，,!！?？\\n]|但是|然而|不过|反而|却|但");
    private static final Pattern PROHIBITION = Pattern.compile("(?:禁止|严禁|不得|不允许|不可以|不可|不要|不能|不会|不应|不准|不许|不该|不)$");
    private static final Pattern OTHER_NEGATION = Pattern.compile("不|未|无|非|否|别|没|莫");
    private static final Pattern MARKUP = Pattern.compile("[*_`]");

    static boolean present(Object value, String forbidden) {
        return present(value, forbidden, 0);
    }

    private static boolean present(Object value, String forbidden, int depth) {
        if (depth > 64) return true;
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().anyMatch(entry ->
                present(entry.getKey(), forbidden, depth + 1) || present(entry.getValue(), forbidden, depth + 1));
        if (value instanceof Iterable<?> values) {
            for (Object item : values) if (present(item, forbidden, depth + 1)) return true;
            return false;
        }
        if (value == null) return false;
        String text = MARKUP.matcher(String.valueOf(value).toLowerCase(Locale.ROOT)).replaceAll("");
        for (int start = text.indexOf(forbidden); start >= 0; start = text.indexOf(forbidden, start + forbidden.length())) {
            int boundary = 0;
            var clauses = CLAUSE.matcher(text.substring(0, start));
            while (clauses.find()) boundary = clauses.end();
            String prefix = text.substring(boundary, start).strip();
            var prohibition = PROHIBITION.matcher(prefix);
            // Never borrow a negation from a different field, sentence, or a double negative.
            if (!prohibition.find() || OTHER_NEGATION.matcher(prefix.substring(0, prohibition.start())).find()) return true;
        }
        return false;
    }
}
