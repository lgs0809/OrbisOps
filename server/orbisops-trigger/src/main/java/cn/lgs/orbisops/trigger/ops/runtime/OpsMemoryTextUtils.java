package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpsMemoryTextUtils {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9_./:-]{2,}");

    private OpsMemoryTextUtils() {
    }

    static List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            terms.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return terms.stream().distinct().toList();
    }

    static Map<String, Object> parseMetadata(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            return JSON.parseObject(text);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 0) {
            return "";
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    static int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int chinese = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chinese++;
            } else if (!Character.isWhitespace(c)) {
                other++;
            }
        }
        return chinese + Math.max(1, other / 4);
    }

    static String stableHash(String value) {
        return DigestUtils.md5DigestAsHex((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    static String now(java.time.format.DateTimeFormatter formatter) {
        return formatter.format(java.time.LocalDateTime.now());
    }

}
