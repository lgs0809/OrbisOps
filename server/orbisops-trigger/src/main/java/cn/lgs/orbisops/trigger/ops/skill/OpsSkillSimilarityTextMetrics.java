package cn.lgs.orbisops.trigger.ops.skill;

import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure text normalization, Dice, Jaccard, abbreviation, and containment metrics. */
final class OpsSkillSimilarityTextMetrics {

    double dice(String left, String right) {
        Set<String> x = grams(left);
        Set<String> y = grams(right);
        if (x.isEmpty() || y.isEmpty()) {
            return 0D;
        }
        Set<String> intersection = new HashSet<>(x);
        intersection.retainAll(y);
        return 2D * intersection.size() / (x.size() + y.size());
    }

    double jaccard(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0D;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty()
                ? 0D
                : (double) intersection.size() / union.size();
    }

    boolean containsIgnoreCase(String value, String expected) {
        return StringUtils.hasText(expected)
                && text(value).toLowerCase(Locale.ROOT)
                .contains(expected.toLowerCase(Locale.ROOT));
    }

    String normalize(String value) {
        return text(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    String abbreviate(String value, int max) {
        String normalized = text(value);
        return normalized.length() <= max
                ? normalized
                : normalized.substring(0, max);
    }

    String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Set<String> grams(String value) {
        String normalized = normalize(value).replace(" ", "");
        Set<String> grams = new HashSet<>();
        if (normalized.length() == 1) {
            grams.add(normalized);
        }
        for (int index = 0; index < normalized.length() - 1; index++) {
            grams.add(normalized.substring(index, index + 2));
        }
        return grams;
    }
}
