package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.net.URI;

/** Parses project resource endpoints without knowing MCP tool or credential semantics. */
final class OpsProjectMcpEndpointParser {

    private final OpsProjectMcpTypePolicy typePolicy;

    OpsProjectMcpEndpointParser(OpsProjectMcpTypePolicy typePolicy) {
        this.typePolicy = typePolicy;
    }

    URI resourceUri(String endpoint, String defaultScheme) {
        String value = OpsProjectMcpConfigValues.text(
                endpoint,
                typePolicy.defaultEndpoint(defaultScheme));
        if (value.startsWith("jdbc:")) {
            value = value.substring("jdbc:".length());
        }
        if (!value.contains("://")) {
            value = defaultScheme + "://" + value;
        }
        return URI.create(value);
    }

    String databaseName(String endpoint, String type) {
        URI uri = resourceUri(endpoint, type);
        String path = OpsProjectMcpConfigValues.text(uri.getPath(), "");
        if (!StringUtils.hasText(path) || "/".equals(path)) return "";
        return path.replaceFirst("^/", "").split("/")[0];
    }
}
