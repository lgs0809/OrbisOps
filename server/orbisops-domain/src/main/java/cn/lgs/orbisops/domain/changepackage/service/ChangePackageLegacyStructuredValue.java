package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

/** Decodes legacy ChangePackage fields that may be a typed value or JSON text. */
final class ChangePackageLegacyStructuredValue {

    private ChangePackageLegacyStructuredValue() {
    }

    static Object decode(Object raw) {
        if (raw instanceof String value && !value.isBlank()) {
            String trimmed = value.trim();
            if (trimmed.startsWith("{")) return CanonicalJson.parseObject(trimmed);
            if (trimmed.startsWith("[")) return CanonicalJson.parseArray(trimmed);
        }
        return raw;
    }
}
