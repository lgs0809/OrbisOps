package cn.lgs.orbisops.domain.project.model;

import java.util.Locale;

public record ProjectResourceType(String value) {

    public ProjectResourceType {
        value = normalize(value);
    }

    public static ProjectResourceType from(String value) {
        return new ProjectResourceType(value);
    }

    public String defaultEndpoint() {
        return switch (value) {
            case "mysql" -> "mysql://127.0.0.1:3306/app";
            case "postgresql" -> "postgresql://127.0.0.1:5432/app";
            case "redis" -> "redis://127.0.0.1:6379/0";
            case "rabbitmq" -> "http://127.0.0.1:15672";
            case "elasticsearch" -> "http://127.0.0.1:9200";
            case "prometheus" -> "http://127.0.0.1:9090";
            case "openapi" -> "http://127.0.0.1:8080/v3/api-docs";
            case "service_control" -> "service-control://service";
            default -> "local";
        };
    }

    private static String normalize(String input) {
        String normalized = input == null || input.trim().isBlank()
                ? "mysql"
                : input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "pgsql", "pg" -> "postgresql";
            case "elk", "es" -> "elasticsearch";
            case "k8s" -> "kubernetes";
            case "gitlab" -> "gitlab_ci";
            case "http", "api" -> "http_api";
            default -> normalized;
        };
    }
}
