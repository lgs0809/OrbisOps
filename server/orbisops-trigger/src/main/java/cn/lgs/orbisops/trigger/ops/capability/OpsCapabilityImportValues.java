package cn.lgs.orbisops.trigger.ops.capability;

import org.springframework.util.StringUtils;

/** Shared scalar normalization for capability import boundaries. */
final class OpsCapabilityImportValues {

    private OpsCapabilityImportValues() {
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static String require(String value, String code) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(code);
        }
        return value.trim();
    }

    static String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }
}
