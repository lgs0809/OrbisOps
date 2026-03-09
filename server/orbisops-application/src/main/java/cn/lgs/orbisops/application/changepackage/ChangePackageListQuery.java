package cn.lgs.orbisops.application.changepackage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChangePackageListQuery(Map<String, Object> filters, int limit) {
    public ChangePackageListQuery {
        filters = filters == null || filters.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(filters));
        if (limit <= 0 || limit > 1000) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LIST_LIMIT_INVALID");
        }
    }
}
