package cn.lgs.orbisops.trigger.ops.rag.advisor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Framework-neutral tokenization and candidate-term policy shared by lexical recall and diversity scoring.
 */
public final class RagLexicalTextPolicy {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9_./:-]{2,}");

    public List<String> tokenize(String text) {
        if (!hasText(text)) {
            return List.of();
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(lowerText);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            tokens.add(token);
            if (containsChinese(token)) {
                tokens.addAll(chineseNgrams(token, 2));
                tokens.addAll(chineseNgrams(token, 3));
            } else {
                tokens.addAll(splitAsciiToken(token));
            }
        }
        return tokens.stream()
                .filter(this::hasText)
                .collect(Collectors.toList());
    }

    public List<String> candidateTerms(List<String> queryTerms, int maxTerms) {
        if (queryTerms == null || queryTerms.isEmpty() || maxTerms <= 0) {
            return List.of();
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        queryTerms.stream()
                .filter(this::hasText)
                .filter(term -> term.length() >= 2)
                .filter(this::isTechnicalTerm)
                .forEach(selected::add);
        queryTerms.stream()
                .filter(this::hasText)
                .filter(term -> term.length() >= 2)
                .forEach(selected::add);
        return selected.stream().limit(maxTerms).collect(Collectors.toList());
    }

    private boolean isTechnicalTerm(String term) {
        return hasText(term) && term.matches(".*[A-Za-z0-9_./:-].*");
    }

    private boolean containsChinese(String value) {
        if (!hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
            if (Character.UnicodeScript.HAN.equals(script)) {
                return true;
            }
        }
        return false;
    }

    private List<String> chineseNgrams(String token, int n) {
        if (token == null || token.length() < n) {
            return List.of();
        }
        List<String> ngrams = new ArrayList<>();
        for (int i = 0; i <= token.length() - n; i++) {
            ngrams.add(token.substring(i, i + n));
        }
        return ngrams;
    }

    private List<String> splitAsciiToken(String token) {
        if (!hasText(token)) {
            return List.of();
        }
        return Arrays.stream(token.split("[_./:-]+"))
                .filter(part -> part.length() >= 2)
                .collect(Collectors.toList());
    }

    private boolean hasText(CharSequence value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
