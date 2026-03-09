package cn.lgs.orbisops.domain.shared.json;

import java.beans.Introspector;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Deterministic, framework-free JSON protocol used by domain hashes, manifests
 * and immutable snapshots.
 *
 * <p>Map keys are always serialized in lexical order. Parsing accepts standard
 * JSON only and returns mutable {@link LinkedHashMap}/{@link ArrayList}
 * structures so callers can safely construct derived snapshots.</p>
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String stringify(Object value) {
        return stringify(value, true);
    }

    public static String stringifyPreservingOrder(Object value) {
        return stringify(value, false);
    }

    private static String stringify(Object value, boolean sortMapKeys) {
        StringBuilder output = new StringBuilder();
        writeJson(normalize(value, sortMapKeys, new IdentityHashMap<>()), output);
        return output.toString();
    }

    public static Map<String, Object> parseObject(String value) {
        if (value == null || value.trim().isEmpty()) return new LinkedHashMap<>();
        Object parsed = parse(value);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON_OBJECT_REQUIRED");
        }
        return objectCopy(map);
    }

    public static List<Object> parseArray(String value) {
        if (value == null || value.trim().isEmpty()) return new ArrayList<>();
        Object parsed = parse(value);
        if (!(parsed instanceof List<?> list)) {
            throw new IllegalArgumentException("JSON_ARRAY_REQUIRED");
        }
        return listCopy(list);
    }

    public static Object parse(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return new Parser(value).parse();
    }

    public static Map<String, Object> copyObject(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return new LinkedHashMap<>();
        return objectCopy(source);
    }

    private static Object normalize(
            Object value,
            boolean sortMapKeys,
            IdentityHashMap<Object, Boolean> visiting) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character character) return String.valueOf(character);
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        if (value instanceof TemporalAccessor
                || value instanceof UUID
                || value instanceof URI
                || value instanceof Path) {
            return value.toString();
        }
        if (value instanceof Optional<?> optional) {
            return normalize(optional.orElse(null), sortMapKeys, visiting);
        }
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("CANONICAL_JSON_CYCLE_DETECTED");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> normalized = sortMapKeys
                        ? new TreeMap<>()
                        : new LinkedHashMap<>();
                map.forEach((key, item) -> normalized.put(
                        String.valueOf(key),
                        normalize(item, sortMapKeys, visiting)));
                return normalized;
            }
            if (value instanceof Iterable<?> iterable) {
                List<Object> result = new ArrayList<>();
                iterable.forEach(item -> result.add(normalize(item, sortMapKeys, visiting)));
                return result;
            }
            if (value.getClass().isArray()) {
                List<Object> result = new ArrayList<>();
                for (int index = 0; index < Array.getLength(value); index++) {
                    result.add(normalize(Array.get(value, index), sortMapKeys, visiting));
                }
                return result;
            }
            Map<String, Object> properties = beanProperties(value, sortMapKeys, visiting);
            if (!properties.isEmpty()) return properties;
            throw new IllegalArgumentException(
                    "CANONICAL_JSON_UNSUPPORTED_TYPE:" + value.getClass().getName());
        } finally {
            visiting.remove(value);
        }
    }

    private static Map<String, Object> beanProperties(
            Object value,
            boolean sortMapKeys,
            IdentityHashMap<Object, Boolean> visiting) {
        Map<String, Object> properties = sortMapKeys
                ? new TreeMap<>()
                : new LinkedHashMap<>();
        Class<?> type = value.getClass();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                properties.put(component.getName(), invoke(
                        value, component.getAccessor(), sortMapKeys, visiting));
            }
            return properties;
        }
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.isSynthetic()
                    || method.getParameterCount() != 0
                    || method.getReturnType() == Void.TYPE
                    || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String property = propertyName(method);
            if (property != null && !property.isBlank()) {
                properties.put(property, invoke(value, method, sortMapKeys, visiting));
            }
        }
        return properties;
    }

    private static Object invoke(
            Object target,
            Method method,
            boolean sortMapKeys,
            IdentityHashMap<Object, Boolean> visiting) {
        try {
            return normalize(method.invoke(target), sortMapKeys, visiting);
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException(
                    "CANONICAL_JSON_PROPERTY_READ_FAILED:" + method.getName(),
                    error);
        }
    }

    private static String propertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return Introspector.decapitalize(name.substring(3));
        }
        if (name.startsWith("is")
                && name.length() > 2
                && (method.getReturnType() == boolean.class
                || method.getReturnType() == Boolean.class)) {
            return Introspector.decapitalize(name.substring(2));
        }
        return null;
    }

    private static void writeJson(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
            return;
        }
        if (value instanceof String text) {
            writeString(text, output);
            return;
        }
        if (value instanceof Boolean bool) {
            output.append(bool);
            return;
        }
        if (value instanceof Number number) {
            writeNumber(number, output);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.append(',');
                first = false;
                writeString(String.valueOf(entry.getKey()), output);
                output.append(':');
                writeJson(entry.getValue(), output);
            }
            output.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            output.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) output.append(',');
                first = false;
                writeJson(item, output);
            }
            output.append(']');
            return;
        }
        throw new IllegalArgumentException(
                "CANONICAL_JSON_UNSUPPORTED_VALUE:" + value.getClass().getName());
    }

    private static void writeNumber(Number number, StringBuilder output) {
        if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
            throw new IllegalArgumentException("CANONICAL_JSON_NON_FINITE_NUMBER");
        }
        if (number instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw new IllegalArgumentException("CANONICAL_JSON_NON_FINITE_NUMBER");
        }
        output.append(number.toString());
    }

    private static void writeString(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append("\\u");
                        String hex = Integer.toHexString(character);
                        output.append("0".repeat(4 - hex.length())).append(hex);
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static Object mutableCopy(Object value) {
        if (value instanceof Map<?, ?> map) return objectCopy(map);
        if (value instanceof List<?> list) return listCopy(list);
        return value;
    }

    private static Map<String, Object> objectCopy(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), mutableCopy(item)));
        return result;
    }

    private static List<Object> listCopy(List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        list.forEach(item -> result.add(mutableCopy(item)));
        return result;
    }

    private static final class Parser {

        private final String source;
        private int cursor;

        private Parser(String source) {
            this.source = source;
        }

        private Object parse() {
            skipWhitespace();
            Object value = readValue();
            skipWhitespace();
            if (cursor != source.length()) {
                throw invalidJson();
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (cursor >= source.length()) throw invalidJson();
            return switch (source.charAt(cursor)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            cursor++;
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) return result;
            while (true) {
                skipWhitespace();
                if (cursor >= source.length() || source.charAt(cursor) != '"') {
                    throw invalidJson();
                }
                String key = readString();
                skipWhitespace();
                require(':');
                result.put(key, readValue());
                skipWhitespace();
                if (consume('}')) return result;
                require(',');
            }
        }

        private List<Object> readArray() {
            cursor++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) return result;
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (consume(']')) return result;
                require(',');
            }
        }

        private String readString() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (cursor < source.length()) {
                char character = source.charAt(cursor++);
                if (character == '"') return result.toString();
                if (character != '\\') {
                    if (character < 0x20) throw invalidJson();
                    result.append(character);
                    continue;
                }
                if (cursor >= source.length()) throw invalidJson();
                char escaped = source.charAt(cursor++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(readUnicode());
                    default -> throw invalidJson();
                }
            }
            throw invalidJson();
        }

        private char readUnicode() {
            if (cursor + 4 > source.length()) throw invalidJson();
            String hex = source.substring(cursor, cursor + 4);
            cursor += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException error) {
                throw invalidJson();
            }
        }

        private Object readNumber() {
            int start = cursor;
            if (consume('-') && cursor >= source.length()) throw invalidJson();
            if (consume('0')) {
                if (cursor < source.length() && Character.isDigit(source.charAt(cursor))) {
                    throw invalidJson();
                }
            } else {
                readDigits();
            }
            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                readDigits();
            }
            if (cursor < source.length()
                    && (source.charAt(cursor) == 'e' || source.charAt(cursor) == 'E')) {
                decimal = true;
                cursor++;
                if (cursor < source.length()
                        && (source.charAt(cursor) == '+' || source.charAt(cursor) == '-')) {
                    cursor++;
                }
                readDigits();
            }
            String token = source.substring(start, cursor);
            try {
                if (decimal) return new BigDecimal(token);
                BigInteger integer = new BigInteger(token);
                if (integer.bitLength() < 31) return integer.intValue();
                if (integer.bitLength() < 63) return integer.longValue();
                return integer;
            } catch (NumberFormatException error) {
                throw invalidJson();
            }
        }

        private void readDigits() {
            int start = cursor;
            while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) {
                cursor++;
            }
            if (cursor == start) throw invalidJson();
        }

        private Object readLiteral(String literal, Object value) {
            if (!source.startsWith(literal, cursor)) throw invalidJson();
            cursor += literal.length();
            return value;
        }

        private void require(char expected) {
            skipWhitespace();
            if (!consume(expected)) throw invalidJson();
        }

        private boolean consume(char expected) {
            if (cursor < source.length() && source.charAt(cursor) == expected) {
                cursor++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (cursor < source.length()) {
                char character = source.charAt(cursor);
                if (character == ' ' || character == '\n' || character == '\r' || character == '\t') {
                    cursor++;
                } else {
                    return;
                }
            }
        }

        private IllegalArgumentException invalidJson() {
            return new IllegalArgumentException("INVALID_JSON_AT_POSITION:" + cursor);
        }
    }
}
