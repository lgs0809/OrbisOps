package cn.lgs.orbisops.trigger.application.project;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only Redis key discovery using the RESP protocol. */
@Component
public class OpsProjectRedisSchemaProbe implements OpsProjectResourceSchemaProbe {

    @Override
    public boolean supports(String resourceType) {
        return "redis".equals(resourceType);
    }

    @Override
    public Map<String, Object> scan(String resourceType,
                                    String endpoint,
                                    Map<String, Object> credential) throws IOException {
        URI uri = resourceUri(endpoint);
        String host = text(uri.getHost(), "127.0.0.1");
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;
        List<Map<String, Object>> objects = new ArrayList<>();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(3000);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            String password = text(credential.get("password"), "");
            if (StringUtils.hasText(password)) {
                redisCommand(output, "AUTH", password);
                readRedis(input);
            }
            String database = databaseName(uri);
            if (StringUtils.hasText(database) && !"0".equals(database)) {
                redisCommand(output, "SELECT", database);
                readRedis(input);
            }
            redisCommand(output, "SCAN", "0", "COUNT", "50");
            for (String key : redisScanKeys(readRedis(input)).stream().limit(20).toList()) {
                redisCommand(output, "TYPE", key);
                String dataType = String.valueOf(readRedis(input)).toUpperCase(Locale.ROOT);
                objects.add(Map.of(
                        "name", key,
                        "dataType", dataType,
                        "comment", "Redis key"));
            }
        }
        return Map.of(
                "objects", objects,
                "scannedAt", LocalDateTime.now().toString());
    }

    private URI resourceUri(String endpoint) {
        String value = endpoint;
        if (!value.contains("://")) {
            value = "redis://" + value;
        }
        return URI.create(value);
    }

    private String databaseName(URI uri) {
        String path = text(uri.getPath(), "");
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "";
        }
        return path.replaceFirst("^/", "").split("/")[0];
    }

    private void redisCommand(BufferedOutputStream output, String... args) throws IOException {
        output.write(("*" + args.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.flush();
    }

    private Object readRedis(BufferedInputStream input) throws IOException {
        int prefix = input.read();
        if (prefix == -1) {
            throw new IOException("Redis 连接已关闭");
        }
        return switch ((char) prefix) {
            case '+' -> readRedisLine(input);
            case '-' -> throw new IOException(readRedisLine(input));
            case ':' -> Long.parseLong(readRedisLine(input));
            case '$' -> readRedisBulk(input);
            case '*' -> readRedisArray(input);
            default -> throw new IOException("未知 Redis 响应：" + (char) prefix);
        };
    }

    private String readRedisBulk(BufferedInputStream input) throws IOException {
        int length = Integer.parseInt(readRedisLine(input));
        if (length < 0) {
            return "";
        }
        byte[] bytes = input.readNBytes(length);
        input.readNBytes(2);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<Object> readRedisArray(BufferedInputStream input) throws IOException {
        int length = Integer.parseInt(readRedisLine(input));
        if (length < 0) {
            return List.of();
        }
        List<Object> values = new ArrayList<>();
        for (int index = 0; index < length; index++) {
            values.add(readRedis(input));
        }
        return values;
    }

    private String readRedisLine(BufferedInputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        int current;
        while ((current = input.read()) != -1) {
            if (current == '\r') {
                input.read();
                break;
            }
            builder.append((char) current);
        }
        return builder.toString();
    }

    private List<String> redisScanKeys(Object response) {
        if (response instanceof List<?> list
                && list.size() >= 2
                && list.get(1) instanceof List<?> keys) {
            return keys.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of();
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
