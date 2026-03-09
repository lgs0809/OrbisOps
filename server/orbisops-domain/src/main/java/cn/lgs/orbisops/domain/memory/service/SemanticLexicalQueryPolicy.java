package cn.lgs.orbisops.domain.memory.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic tokenization and PostgreSQL web-search query rendering policy. */
public class SemanticLexicalQueryPolicy {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[\\p{IsHan}]{2,}|[A-Za-z0-9_./:-]{2,}");

    public List<String> terms(String text) {
        if (!hasText(text)) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            terms.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return terms.stream()
                .filter(term -> term.length() >= 2)
                .distinct()
                .limit(12)
                .toList();
    }

    public String webSearchQuery(String text) {
        List<String> terms = terms(text);
        return terms.isEmpty() ? "" : String.join(" OR ", terms);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
