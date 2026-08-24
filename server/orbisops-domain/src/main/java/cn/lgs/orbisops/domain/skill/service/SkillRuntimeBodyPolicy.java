package cn.lgs.orbisops.domain.skill.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** UTF-8 bytes are a conservative upper bound, never an asserted model-token measurement.
 * Only explicitly marked optional modules may be omitted; unstructured Markdown is mandatory.
 * Safety, preconditions and verification belong outside optional markers in reviewed packages.
 */
public final class SkillRuntimeBodyPolicy {
    public static final int TOTAL_BUDGET = 6000;
    public static final int MAX_SKILLS = 3;
    public static final int CATALOG_ENTRY_BUDGET = 300;
    private static final Pattern OPEN = Pattern.compile("<!-- orbisops:optional id=([a-zA-Z0-9_-]{1,64}) keywords=([^\r\n<>]{1,200}) -->");
    private static final String CLOSE = "<!-- /orbisops:optional -->";
    public String project(String body, String query, int limit) {
        String raw = body == null ? "" : body;
        int budget = Math.min(TOTAL_BUDGET, Math.max(0, limit));
        List<Part> parts = new ArrayList<>(); Set<String> ids = new HashSet<>();
        int cursor = 0;
        var matcher = OPEN.matcher(raw);
        while (matcher.find(cursor)) {
            String required = raw.substring(cursor, matcher.start());
            rejectMarker(required);
            parts.add(new Part(required, false, false));
            int end = raw.indexOf(CLOSE, matcher.end());
            if (end < 0 || !ids.add(matcher.group(1))) throw invalid();
            String optional = raw.substring(matcher.end(), end);
            rejectMarker(optional);
            String input = (query == null ? "" : query).toLowerCase(Locale.ROOT);
            boolean matches = Arrays.stream(matcher.group(2).split("[|,]"))
                    .map(String::trim).filter(s -> !s.isBlank())
                    .anyMatch(s -> input.contains(s.toLowerCase(Locale.ROOT)));
            parts.add(new Part(optional, true, matches));
            cursor = end + CLOSE.length();
        }
        String tail = raw.substring(cursor); rejectMarker(tail);
        parts.add(new Part(tail, false, false));
        long mandatory = parts.stream().filter(p -> !p.optional()).mapToLong(p -> units(p.content())).sum();
        if (mandatory > budget) throw new IllegalStateException("SKILL_REQUIRED_BODY_BUDGET_EXCEEDED");
        int remaining = budget - (int) mandatory;
        StringBuilder selected = new StringBuilder();
        for (Part part : parts) {
            if (!part.optional()) selected.append(part.content());
            else if (part.matches() && units(part.content()) <= remaining) {
                selected.append(part.content()); remaining -= units(part.content());
            }
        }
        return selected.toString();
    }
    public static int units(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
    private static void rejectMarker(String value) {
        if (value.contains("orbisops:optional")) throw invalid();
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("SKILL_OPTIONAL_MODULE_MARKUP_INVALID");
    }
    private record Part(String content, boolean optional, boolean matches) { }
}
